package com.sb.elsinore.triggers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;

import com.sb.common.SBStringUtils;
import com.sb.elsinore.notificiations.Notifications;
import com.sb.elsinore.notificiations.WebNotification;
import org.json.simple.JSONObject;
import org.rendersnake.HtmlCanvas;
import org.rendersnake.tools.PrettyWriter;

import static org.rendersnake.HtmlAttributesFactory.*;

import com.sb.elsinore.BrewDay;
import com.sb.elsinore.BrewServer;
import com.sb.elsinore.LaunchControl;
import com.sb.elsinore.Messages;
import com.sb.elsinore.PID;
import com.sb.elsinore.Temp;

/**
 * A TemperatureTrigger will hold until the specified probe hits the target
 * temperature when active.
 * If a PID is associated with the temperature probe, the PID set point will
 * be set to the target temperature associated with this TemperatureTrigger.
 * @author Doug Edey
 *
 */
@SuppressWarnings("unused")
public class TemperatureTrigger implements TriggerInterface {

    private BigDecimal targetTemp = null;
    private Temp temperatureProbe = null;
    private String method = null;
    private String type = null;
    private boolean active;
    private int position = -1;
    public static String INCREASE = "INCREASE";
    public static String DECREASE = "DECREASE";
    private String mode = null;
    private Date startDate = null;
    private BigDecimal exitTemp;
    private WebNotification webNotification = null;

    public TemperatureTrigger() {
        BrewServer.LOG.info("Created an empty Temperature Trigger");
    }

    public TemperatureTrigger(int newPosition) {
        BrewServer.LOG.info("Created a Temperature Trigger at" + newPosition);
        this.position = newPosition;
    }

    public TemperatureTrigger(int position, String tempProbe, double targetTemp, String stepType, String stepMethod) {
        this.position = position;
        this.targetTemp = new BigDecimal(targetTemp);
        this.temperatureProbe = LaunchControl.findTemp(tempProbe);
        this.type = stepType;
        this.method = stepMethod;
    }

    /**
     * Set the {@link java.util.Date} that this step is started.
     * @param inStart The Date that this step is started.
     */
    private void setStart(final Date inStart) {
        this.startDate = inStart;
    }

    /**
     * Set The Target temperature of the PID
     * Associated with this temperatureTrigger.
     */
    public final void setTargetTemperature() {
        PID pid = LaunchControl.findPID(this.temperatureProbe.getName());
        if (pid == null) {
            LaunchControl.setMessage(temperatureProbe.getName()
                + " is not associated with a PID. "
                + "Trigger will wait for it to hit the target temperature.");
        } else {
            pid.setTemp(this.targetTemp);
        }
    }

    /**
     * Set The Target temperature of the PID
     * Associated with this temperatureTrigger.
     */
    public final void setExitTemperature() {
        PID pid = LaunchControl.findPID(this.temperatureProbe.getName());
        if (pid == null) {
            LaunchControl.setMessage(temperatureProbe.getName()
                    + " is not associated with a PID. "
                    + "Trigger will wait for it to hit the target temperature.");
        } else {
            pid.setTemp(this.exitTemp);
        }
    }

    /**
     * Set the temperature probe to use for this trigger by name.
     * @param name The name of the probe to lookup.
     */
    public final void setTemperatureProbe(final String name) {
        temperatureProbe = LaunchControl.findTemp(name);
    }

    /**
     * Creates a new TemperatureTrigger at a set position with parameters.
     * @param inPosition The position this Trigger is at.
     * @param parameters The Parameters to setup this TemperatureTrigger.
     * "temp": The TargetTemperature for this step.
     * "method": A string used to represent this trigger.
     * "type": A String used to represent this trigger.
     * "tempprobe": The name of the temperature probe to use.
     */
    public TemperatureTrigger(final int inPosition,
            final JSONObject parameters) {
        this.position = inPosition;
        BigDecimal tTemp = new BigDecimal(
              parameters.get("targetTemperature").toString().replace(",", "."));

        String inMethod = parameters.get("method").toString();
        String inType = parameters.get("stepType").toString();
        String inTempProbe = parameters.get("tempprobe").toString();
        String inMode = parameters.get("mode").toString();

        this.targetTemp = tTemp;
        this.temperatureProbe = LaunchControl.findTemp(inTempProbe);
        this.method = inMethod;
        this.type = inType;
        this.mode = inMode;
    }

    /**
     * Update the current trigger.
     */
    @Override
    public void updateTrigger(final JSONObject parameters) {
        BigDecimal tTemp = new BigDecimal(
            parameters.get("targetTemperature").toString().replace(",", "."));

        String newMode = parameters.get("mode").toString();
        String newMethod = parameters.get("method").toString();
        String newType = parameters.get("stepType").toString();
        String newTempProbe = parameters.get("tempprobe").toString();
        String exitTemp = parameters.get("exitTemperature").toString();

        this.targetTemp = tTemp;
        if (exitTemp.equals("")) {
            this.exitTemp = this.targetTemp;
        } else {
            this.exitTemp = new BigDecimal(exitTemp.replace(",", "."));
        }
        this.temperatureProbe = LaunchControl.findTemp(newTempProbe);
        this.method = newMethod;
        this.type = newType;
        this.mode = newMode;

        if (this.active) {
            setTargetTemperature();
        }
    }

    /**
     * A blocking call that waits for a trigger to be hit.
     */
    @Override
    public final void waitForTrigger() {

        if (targetTemp == null) {
            BrewServer.LOG.warning("No Target Temperature Set");
            return;
        }
        if (temperatureProbe == null) {
            BrewServer.LOG.warning("No Temperature Probe Set");
            return;
        }

        if (mode == null) {
            BrewServer.LOG.warning("No Mode Set");
            return;
        }

        setTargetTemperature();
        setStart(new Date());
        if (this.mode == null) {
            // Just get to within 2F of the target Temp.
            BrewServer.LOG.warning("Waiting to be within 2F of " + targetTemp);
            while(temperatureProbe.convertF(temperatureProbe.getTemp().subtract(targetTemp).abs())
                    .compareTo(new BigDecimal(2.0)) <= 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    BrewServer.LOG.warning("Temperature Trigger interrupted.");
                }
            }
        } else if (this.mode.equals(TemperatureTrigger.INCREASE)) {
            while (this.temperatureProbe.getTemp().compareTo(
                    this.targetTemp) <= 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    BrewServer.LOG.warning("Temperature Trigger interrupted");
                    return;
                }
            }
        } else if (this.mode.equals(TemperatureTrigger.DECREASE)) {
            while (this.temperatureProbe.getTemp().compareTo(
                    this.targetTemp) >= 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    BrewServer.LOG.warning("Temperature Trigger interrupted");
                    return;
                }
            }
        } else {
            // Just get to within 2F of the target Temp.
            BrewServer.LOG.warning("Waiting to be within 2F of " + targetTemp);
            while(temperatureProbe.convertF(temperatureProbe.getTemp().subtract(targetTemp).abs())
                    .compareTo(new BigDecimal(2.0)) <= 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    BrewServer.LOG.warning("Temperature Trigger interrupted.");
                }
            }
        }

        if (exitTemp.compareTo(targetTemp) != 0) {
            setExitTemperature();
        }
    }

    /**
     * Return true if this TemperatureTrigger is activated.
     * @return True if active.
     */
    @Override
    public final boolean isActive() {
        return this.active;
    }

    /**
     * Get the current position of this TemperatureTrigger in the overall list.
     * @return The position of this TemperatureTrigger
     */
    @Override
    public final int getPosition() {
        return this.position;
    }

    /**
     * Set the position of this step.
     * @param newPosition The new position.
     */
    @Override
    public final void setPosition(final int newPosition) {
        this.position = newPosition;
    }

    /**
     * Activate this TemperatureTrigger step.
     */
    @Override
    public final void setActive() {
        this.active = true;
        createNotifications(String.format(Messages.TARGET_TEMP_TRIGGER, targetTemp, temperatureProbe.getScale()));
    }

    /**
     * Deactivate this TemperatureTrigger step.
     */
    @Override
    public final void deactivate() {
        this.active = false;
        clearNotifications();
    }

    /**
     * Get the Form HTML Canvas representing a temperature trigger.
     * @return {@link org.rendersnake.HtmlCanvas} representing the input form.
     * @throws IOException when the HTMLCanvas could not be created.
     */
    @Override
    public final HtmlCanvas getForm() throws IOException {
        HtmlCanvas html = new HtmlCanvas(new PrettyWriter());
        html.div(id("NewTempTrigger").class_(""));
            html.form(id("newTriggersForm"));
                html.input(id("type").name("type")
                        .hidden("true").value("Temperature"));
                html.input(id("type").name("position")
                        .hidden("position").value("" + this.position));
                html.input(class_("inputBox temperature form-control")
                        .type("number").add("step", "any")
                        .add("placeholder", Messages.SET_POINT)
                        .name("targetTemperature").value(""));
                html.input(class_("inputBox temperature form-control")
                    .type("number").add("step", "any")
                    .add("placeholder", Messages.END_TEMP)
                    .name("exitTemperature").value(""));
                html.input(class_("inputBox form-control")
                        .name("method").value("")
                        .add("placeholder", Messages.METHOD));
                html.input(class_("inputBox form-control")
                        .name("stepType").value("")
                        .add("placeholder", Messages.TYPE));
             // Add the on/off values
                html.select(class_("holo-spinner").name("mode")
                        .id("mode"));
                    html.option(value(""))
                            .write("")
                    ._option();
                    html.option(value(TemperatureTrigger.INCREASE))
                        .write(TemperatureTrigger.INCREASE)
                    ._option();
                    html.option(value(TemperatureTrigger.DECREASE))
                        .write(TemperatureTrigger.DECREASE)
                    ._option();
                html._select();
                html.button(name("submitTemperature")
                        .class_("btn col-md-12")
                        .add("data-toggle", "clickover")
                        .onClick("submitNewTriggerStep(this);"))
                    .write(Messages.ADD_TRIGGER)
                ._button();
            html._form();
        html._div();
        return html;
    }
    /**
     * Get the Form HTML Canvas representing a temperature trigger.
     * @return {@link org.rendersnake.HtmlCanvas} representing the input form.
     * @throws IOException when the HTMLCanvas could not be created.
     */
    @Override
    public final HtmlCanvas getEditForm() throws IOException {
        HtmlCanvas html = new HtmlCanvas(new PrettyWriter());
        html.div(id("EditTempTrigger").class_(""));
            html.form(id("editTriggersForm"));
                html.input(id("type").name("type")
                            .hidden("true").value("Temperature"));
                html.input(id("type").name("position")
                    .hidden("position").value("" + this.position));
                html.input(class_("inputBox temperature form-control")
                    .type("number").add("step", "any")
                    .add("placeholder", Messages.SET_POINT)
                    .value(this.targetTemp.toPlainString())
                    .name("targetTemperature"));
                html.input(class_("inputBox temperature form-control")
                    .type("number").add("step", "any")
                    .add("placeholder", Messages.END_TEMP)
                    .value(this.targetTemp.toPlainString())
                    .name("exitTemperature"));
                html.input(class_("inputBox form-control")
                    .name("method").value("")
                    .value(this.method)
                    .add("placeholder", Messages.METHOD));
                html.input(class_("inputBox form-control")
                    .name("stepType").value("")
                    .value(this.type)
                    .add("placeholder", Messages.TYPE));
                // Add the on/off values
                html.select(class_("holo-spinner").name("mode")
                        .id("mode"));
                    html.option(value(""))
                            .write("")
                    ._option();
                    html.option(value(TemperatureTrigger.INCREASE)
                            .selected_if(
                                this.mode.equals(TemperatureTrigger.INCREASE)))
                        .write(TemperatureTrigger.INCREASE)
                    ._option();
                    html.option(value(TemperatureTrigger.DECREASE)
                            .selected_if(
                                this.mode.equals(TemperatureTrigger.DECREASE)))
                        .write(TemperatureTrigger.DECREASE)
                    ._option();
                html._select();
                html.button(name("submitTemperature")
                        .class_("btn col-md-12")
                        .add("data-toggle", "clickover")
                        .onClick("updateTriggerStep(this);"))
                    .write(Messages.ADD_TRIGGER)
                ._button();
            html._form();
        html._div();
        return html;
    }

    @Override
    public String getName() {
        return "Temperature";
    }

    /**
     * Return the Position, startdate, target temperature, and description.
     * @return The current status as a JSONObject.
     */
    @Override
    public final JSONObject getJSONStatus() {
        String targetTempString = String.format("%.2f", this.targetTemp)
                + this.temperatureProbe.getScale();
        String description = this.method + ": " + this.type;
        if (this.mode != null) {
            description +=" (" + this.mode + ")";
        }

        String startDateStamp = "";
        if (this.startDate != null) {
            startDateStamp = BrewDay.lFormat.format(this.startDate);
        }

        JSONObject currentStatus = new JSONObject();
        currentStatus.put("position", this.position);
        currentStatus.put("start", startDateStamp);
        currentStatus.put("target", targetTempString);
        currentStatus.put("description", description);
        currentStatus.put("active", Boolean.toString(this.active));

        return currentStatus;
    }

    /**
     * Compare by position.
     * @param o the TriggerInterface to compare to.
     * @return Compare.
     */
    @Override
    public final int compareTo(final TriggerInterface o) {
        return (this.position - o.getPosition());
    }

    /**
     * This is for Any device.
     * @return any
     */
    @Override
    public final boolean getTriggerType(String inType) {
        return true;
    }

    public void setTargetTemperature(double stepStartTemp) {
        this.targetTemp = new BigDecimal(stepStartTemp);
    }

    public void setExitTemp(double exitTemp) {
        this.exitTemp = new BigDecimal(exitTemp);
    }

    public BigDecimal getExitTemp() {
        return exitTemp;
    }

    public void createNotifications(String s) {
        if (webNotification != null) {
            //Clear the existing notifications
            clearNotifications();
        }
        webNotification = new WebNotification();
        webNotification.setMessage(s);
        webNotification.sendNotification();
        Notifications.getInstance().addNotification(webNotification);
    }

    public void clearNotifications() {
        if (webNotification == null) {
            return;
        }
        Notifications.getInstance().clearNotification(webNotification);
    }
}
