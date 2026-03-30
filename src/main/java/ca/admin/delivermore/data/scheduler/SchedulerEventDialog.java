package ca.admin.delivermore.data.scheduler;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.stefan.fullcalendar.CalendarView;
import org.vaadin.stefan.fullcalendar.CalendarViewImpl;
import org.vaadin.stefan.fullcalendar.EntryTimeChangedEvent;
import org.vaadin.stefan.fullcalendar.ResourceEntry;
import org.vaadin.stefan.fullcalendar.SchedulerView;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.timepicker.TimePickerVariant;
import com.vaadin.flow.theme.lumo.LumoIcon;

import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.components.custom.Divider;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.SchedulerEventGroupRepository;
import ca.admin.delivermore.data.service.SchedulerEventRepository;
import ca.admin.delivermore.views.UIUtilities;

public class SchedulerEventDialog {
    public enum DialogMode{
        NEW, EDIT, DELETE
    }

    private Logger log = LoggerFactory.getLogger(SchedulerEventDialog.class);
    private Dialog dialog = new Dialog();
    private DialogMode dialogMode = DialogMode.EDIT;

    private Scheduler.EditType editType = Scheduler.EditType.CALENDAR;
    private Button dialogOkButton = new Button();
    private Icon okIcon = LumoIcon.CHECKMARK.create();
    private Button dialogCancelButton = new Button(LumoIcon.CROSS.create());
    private Button dialogResetButton = new Button();

    private Button dialogPublishButton = new Button(new Icon(VaadinIcon.CHECK));

    private Icon resetIcon = LumoIcon.UNDO.create();
    private Button dialogDeleteButton = new Button();
    private Button dialogCloseButton = new Button(LumoIcon.CROSS.create());

    private Boolean validationEnabled = Boolean.FALSE;
    private Boolean hasChangedValues = Boolean.FALSE;

    //private ComboBox<Driver> dialogDriver = new ComboBox<>("Driver");
    private Select<Driver> dialogDriver = new Select<>();
    private RadioButtonGroup<Scheduler.EventType> dialogEventType = new RadioButtonGroup<>();

    private RadioButtonGroup<Scheduler.EventDurationType> dialogDurationType = new RadioButtonGroup<>();
    private DatePicker dialogStartDate = new DatePicker();
    private DatePicker dialogEndDate = new DatePicker();
    private HorizontalLayout dialogTimeSelection = UIUtilities.getHorizontalLayout();

    private TimePicker dialogStartTime = new TimePicker();
    private TimePicker dialogEndTime = new TimePicker();
    private TextField dialogHours = UIUtilities.getTextFieldRO("hrs", "");

    private Checkbox dialogPublished = new Checkbox();

    private Checkbox dialogReoccur = new Checkbox();

    private DatePicker dialogReoccurUntil = new DatePicker();

    private IntegerField dialogReoccurInterval = new IntegerField();

    private HorizontalLayout dialogReoccurSelection = UIUtilities.getHorizontalLayout();

    private DriversRepository driversRepository;
    private SchedulerEventRepository schedulerEventRepository;
    private SchedulerEventGroupRepository schedulerEventGroupRepository;

    private EmailService emailService;

    private SchedulerEvent event;
    private Driver driver;
    private Boolean isOnlyUser = Boolean.TRUE;
    private Long signedInDriverId = 0L;
    private List<Scheduler.EventType> listNoShift = new ArrayList<>();

    private List<SchedulerRefreshNeededListener> schedulerRefreshNeededListeners = new ArrayList<>();

    public SchedulerEventDialog() {
        this.driversRepository = Registry.getBean(DriversRepository.class);
        schedulerEventRepository = Registry.getBean(SchedulerEventRepository.class);
        schedulerEventGroupRepository = Registry.getBean(SchedulerEventGroupRepository.class);
        this.emailService = Registry.getBean(EmailService.class);

        //create a list of EventType to use for Drivers who cannot create Shifts
        listNoShift.add(Scheduler.EventType.UNAVAILABLE);
        listNoShift.add(Scheduler.EventType.OFF);

        dialogConfigure();
    }

    private void dialogConfigure() {
        dialog.getElement().setAttribute("aria-label", "Edit adjustment");

        VerticalLayout dialogLayout = dialogLayout();
        dialog.add(dialogLayout);
        dialog.setHeaderTitle("Event");

        Icon deleteIcon = LumoIcon.CROSS.create();
        deleteIcon.setColor("red");
        dialogDeleteButton.setIcon(deleteIcon);
        dialogDeleteButton.setText("Delete");
        dialogDeleteButton.addClickListener(event -> {
            dialogMode = DialogMode.DELETE;
            dialogDelete();
        });

        dialogCloseButton.addClickListener((e) -> dialog.close());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(dialogCloseButton);
        dialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> dialog.close());

        dialogOkButton.setIcon(okIcon);
        dialogOkButton.setText("Save");
        dialogOkButton.setAutofocus(true);

        dialogOkButton.addClickListener(
                event -> {
                    dialogSave();
                }
        );
        dialogOkButton.addClickShortcut(Key.ENTER);
        dialogOkButton.setEnabled(false);
        dialogOkButton.setDisableOnClick(true);

        dialogResetButton.setText("Reset");
        dialogResetButton.setIcon(resetIcon);
        dialogResetButton.addClickListener(
                event -> {
                    enableOkReset(false);
                    setValues();
                    dialogValidate();
                }
        );

        dialogPublishButton.addClickListener(event -> {
            //publish this single entry
            this.event.setPublished(true);
            schedulerEventRepository.save(this.event);
            String emailTo = driver.getEmail();
            String emailSubject = "DeliverMore Schedule: Time off approved";
            String emailBody = "<p>";
            emailBody += this.event.formatSummaryForNotification();
            emailBody += "</p>";
            emailService.sendMailWithHtmlBody(emailTo, emailSubject, emailBody);
            notifyRefreshNeeded();
            dialog.close();
            UIUtilities.showNotification("Approval sent to " + driver.getName() + " at " + driver.getEmail());
        });
        Icon pubIcon = new Icon(VaadinIcon.CHECK);
        pubIcon.setColor("green");
        dialogPublishButton.setIcon(pubIcon);
        dialogPublishButton.setEnabled(false);
        dialogPublishButton.setVisible(false);

        enableOkReset(false);

        HorizontalLayout footerLayout = new HorizontalLayout(dialogPublishButton,dialogOkButton,dialogDeleteButton,dialogResetButton);

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayout, () -> {}, Key.ENTER)
                .listenOn(footerLayout);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

        dialog.getFooter().add(footerLayout);
    }

    private void enableOkReset(Boolean enable){
        dialogOkButton.setEnabled(enable);
        dialogResetButton.setEnabled(enable);
        if(enable){
            okIcon.setColor("green");
            resetIcon.setColor("blue");
        }else{
            okIcon.setColor(UIUtilities.iconColorNotHighlighted);
            resetIcon.setColor(UIUtilities.iconColorNotHighlighted);
        }
    }

    private void dialogDelete() {
        //delete with confirmation
        log.info("dialogDelete:");

        if(dialogReoccur.getValue()){
            confirmEditReoccurItem(this.event, null);
        }else{
            ConfirmDialog dialogConfirmDelete = new ConfirmDialog();
            dialogConfirmDelete.setHeader("Delete schedule entry?");
            Html text = new Html("<p>Are you sure you want to permanently delete this item?<br>" + event.getDescription() + "</p>");
            dialogConfirmDelete.setText(text);

            dialogConfirmDelete.setCancelable(true);
            //dialog.addCancelListener(event -> setStatus("Canceled"));

            dialogConfirmDelete.setConfirmText("Delete");
            dialogConfirmDelete.setConfirmButtonTheme("error primary");
            dialogConfirmDelete.addConfirmListener(event -> {
                if(isOnlyUser){ //user deleted a timeoff or unavailable so let admin know
                    sendNotificationToAdmin(driver.getName(), this.event, Boolean.TRUE, Boolean.FALSE, driver.getTeamName());
                }

                schedulerEventRepository.delete(this.event);
                dialog.close();
                notifyRefreshNeeded();
            });
            dialogConfirmDelete.open();
        }



    }

    private void dialogSave() {
        log.info("dialogSave:");
        String sourceId = null;
        if(this.event.getId()!=null){
            sourceId = this.event.getId().toString();
        }
        LocalDateTime targetStart = LocalDateTime.of(dialogStartDate.getValue(),dialogStartTime.getValue());
        LocalDateTime targetEnd = LocalDateTime.of(dialogStartDate.getValue(),dialogEndTime.getValue());
        Boolean forceAllDay = false;
        String targetResourceId;
        if(dialogDriver.isEmpty()){
            targetResourceId = Scheduler.availableShiftsResourceId;
            saveDialogEditedEvent(sourceId,targetResourceId,targetStart,targetEnd, forceAllDay);
        }else{
            targetResourceId = dialogDriver.getValue().getFleetId().toString();
            if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY)){
                targetStart = LocalDateTime.of(targetStart.toLocalDate(), Scheduler.minTime);

                if(dialogEventType.getValue().equals(Scheduler.EventType.OFF)){
                    targetEnd = LocalDateTime.of(dialogEndDate.getValue(),Scheduler.maxTime);
                }else{
                    targetEnd = LocalDateTime.of(targetStart.toLocalDate(), Scheduler.maxTime);
                }

            }

            LocalDateTime targetStartWithOffset = targetStart;
            LocalDateTime targetEndWithOffset = targetEnd;

            allowSave(sourceId, targetResourceId, targetStart, targetEnd, targetStartWithOffset, targetEndWithOffset, forceAllDay, null, this.event.getTeamId());
        }
    }

    private void saveDialogEditedEvent(String id, String resourceId, LocalDateTime start, LocalDateTime end, Boolean allDayView ){
        this.event.setType(dialogEventType.getValue());
        String originalResourceId = this.event.getResourceId();
        this.event.setResourceId(resourceId);
        //set the event to NOT published so it will get published later
        //this.event.setPublished(false);

        List<LocalDate> occurences = new ArrayList<>();
        if(dialogReoccur.getValue()){
            //save all event reoccurences
            log.info("saveDialogEditedEvent: reoccur:");

            if(dialogMode.equals(DialogMode.NEW)){
                SchedulerEventGroup eventGroup = new SchedulerEventGroup();
                eventGroup.setReoccurInterval(dialogReoccurInterval.getValue());
                eventGroup.setReoccurUntil(dialogReoccurUntil.getValue());
                eventGroup.setDOW(start.getDayOfWeek());
                occurences = eventGroup.getReoccurDates(start.toLocalDate());

                schedulerEventGroupRepository.save(eventGroup);
                this.event.setEventGroup(eventGroup);
            }else {
                confirmEditReoccurItem(event, originalResourceId);

            }

        }else{
            //check if this is a full day time off with a different end date
            if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY) && dialogEventType.getValue().equals(Scheduler.EventType.OFF) && !dialogStartDate.getValue().equals(dialogEndDate.getValue())){
                //note: datesUntil is not inclusive of end date so need to add 1 day to end date
                List<LocalDate> timeOffDates = dialogStartDate.getValue().datesUntil(dialogEndDate.getValue().plusDays(1L)).collect(Collectors.toList());
                for (LocalDate timeOffDate: timeOffDates) {
                    occurences.add(timeOffDate);
                }
            }else{
                //save the single event
                occurences.add(start.toLocalDate());
            }

        }

        log.info("saveDialogEditedEvent: occurences:" + occurences);
        Integer itemCounter = 0;
        for (LocalDate occurDate: occurences) {
            itemCounter++;
            if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY)){
                log.info("saveDialogEditedEvent: FullDay: occurDate:" + occurDate);
                this.event.setFullDay(true);
                //make sure the start and end are set to the start and end of the day
                this.event.setStart(LocalDateTime.of(occurDate, Scheduler.minTime));
                this.event.setEnd(LocalDateTime.of(occurDate, Scheduler.maxTime));
            }else{
                log.info("saveDialogEditedEvent: occurDate:" + occurDate);
                this.event.setFullDay(false);
                this.event.setStart(LocalDateTime.of(occurDate, start.toLocalTime()));
                this.event.setEnd(LocalDateTime.of(occurDate, end.toLocalTime()));
            }
            log.info("saveDialogEditedEvent: saving occurDate:" + occurDate + " start:" + this.event.getStart() + " end:" + this.event.getEnd());

            //auto publish certain events
            if(this.event.getType().equals(Scheduler.EventType.OFF)){
                if(!isOnlyUser){
                    // no need to send notification for Admin created Time Off as user requested it
                    this.event.setPublished(true);
                }else{
                    //for reoccurring events only notify once
                    if(itemCounter.equals(1)){
                        if(this.event.getFullDay() && !dialogStartDate.getValue().equals(dialogEndDate.getValue())){
                            sendNotificationToAdmin(driver.getName(), this.event, Boolean.FALSE, Boolean.FALSE, driver.getTeamName(), dialogEndDate.getValue());
                        }else{
                            sendNotificationToAdmin(driver.getName(), this.event, Boolean.FALSE, Boolean.FALSE, driver.getTeamName());
                        }
                        UIUtilities.showNotification("Request for time off has been sent for approval!");
                    }
                }
            }else if(this.event.getType().equals(Scheduler.EventType.UNAVAILABLE)){
                if(!isOnlyUser){
                    // no need to send notification for Admin created Unavailable as user requested it
                }else{
                    if(itemCounter.equals(1)){
                        sendNotificationToAdmin(driver.getName(), this.event, Boolean.FALSE, Boolean.FALSE, driver.getTeamName());
                        UIUtilities.showNotification("Admin has been notified");
                    }
                }
                this.event.setPublished(true);
            }else if(dialogMode.equals(DialogMode.NEW)){  //New SHIFT - notification will go when published
                this.event.setPublished(false);
            }else if(dialogMode.equals(DialogMode.EDIT)){  //Edit SHIFT
                if(this.event.getPublished()){
                    //if already published then need to notify driver of change
                    publishedShiftNotifications(this.event,originalResourceId, resourceId);
                } //else notification will go when published
            }

            if(itemCounter.equals(1)){  //for first item save it - the others need to be copies
                schedulerEventRepository.save(this.event);
            }else{
                schedulerEventRepository.save(new SchedulerEvent(this.event));
            }
        }

        notifyRefreshNeeded();
        dialog.close();
    }

    private void publishedShiftNotifications(SchedulerEvent eventForNotification, String originalResourceId, String resourceId){
        if(originalResourceId.equals(resourceId)){ //driver did not change
            sendNotificationToDriver(resourceId,eventForNotification, Boolean.FALSE);
        }else{ //driver changed - notify BOTH old and new driver
            if(originalResourceId.equals("0")){ //assigned shift so only notify new driver
                sendNotificationToDriver(resourceId,eventForNotification, Boolean.FALSE);
            }else if(resourceId.equals("0")){
                //notify original driver shift has been removed from schedule
                sendNotificationToDriver(originalResourceId,eventForNotification, Boolean.TRUE);

                //notify all active drivers shift is now available
                sendNotificationToActiveDrivers(eventForNotification);


            }else{
                sendNotificationToDriver(resourceId,eventForNotification, Boolean.FALSE);
                sendNotificationToDriver(originalResourceId,eventForNotification, Boolean.TRUE);
            }

        }

    }

    private void sendNotificationToActiveDrivers(SchedulerEvent eventForNotification){
        String driverName = Scheduler.availableShiftsDisplayName;
        String driverEmail = null;
        String locationName = null;
        List<Driver> activeDrivers = driversRepository.findActiveByTeamOrderByNameAsc(eventForNotification.getTeamId());
        for (Driver activeDriver: activeDrivers ) {
            if(locationName==null) locationName = activeDriver.getTeamName();
            if(driverEmail==null){
                driverEmail = activeDriver.getEmail();
            }else{
                driverEmail += ", " + activeDriver.getEmail();
            }
        }
        String emailBcc = driverEmail;
        String emailSubject = "DM Schedule: " + driverName + eventForNotification.formatSummaryForNotification();
        String emailBody = "<p>";
        emailBody += "The following shift is now available...<br><br>";

        UIUtilities.showNotification("An available shift has been sent to all active drivers for " + locationName);
        emailBody += eventForNotification.formatSummaryForNotification();

        emailBody += "</p>";
        emailService.sendMailWithHtmlBody("", emailSubject, emailBody, emailBcc);
    }

    private void sendNotificationToDriver(String resourceId, SchedulerEvent eventForNotification, Boolean isDelete){
        Driver driverToNotify = driversRepository.findByFleetId(Long.valueOf(resourceId));
        if(driverToNotify==null){
            log.info("sendNotificationToDriver: resourceId:" + resourceId + " not found - lookup failed");
            return;
        }
        String driverName = driverToNotify.getName();
        String emailTo = driverToNotify.getEmail();
        String emailSubject = "DM Schedule Change: " + driverName + eventForNotification.formatSummaryForNotification();
        String emailBody = "<p>";
        if(isDelete){
            emailBody += "You are no longer scheduled for this shift<br><br>";
            UIUtilities.showNotification("Driver " + driverName + " has been notified they are no longer scheduled for this shift");
        }else{
            emailBody += "This shift has been added to your schedule<br><br>";
            UIUtilities.showNotification("Driver " + driverName + " has been notified this shift added to their schedule");
        }
        emailBody += eventForNotification.formatSummaryForNotification();

        emailBody += "</p>";
        emailService.sendMailWithHtmlBody(emailTo, emailSubject, emailBody);
    }

    private void sendNotificationToAdmin(String driverName, SchedulerEvent eventForNotification, Boolean isDelete, Boolean ignoreReoccurr, String locationName){
        sendNotificationToAdmin(driverName,eventForNotification,isDelete,ignoreReoccurr, locationName,null);
    }

    private void sendNotificationToAdmin(String driverName, SchedulerEvent eventForNotification, Boolean isDelete, Boolean ignoreReoccurr, String locationName, LocalDate includedEndDate){
        String emailTo = "support@delivermore.ca";

        String emailSubject;
        if(includedEndDate!=null){
            emailSubject = "DM Schedule: " + locationName + ": " + driverName + eventForNotification.formatSummaryForNotification(includedEndDate);
        }else{
            emailSubject = "DM Schedule: " + locationName + ": " + driverName + eventForNotification.formatSummaryForNotification();
        }

        String emailBody = "<p>";
        if(isDelete){
            emailBody += "Driver deleted this entry<br><br>";
        }else{
            emailBody += "Driver created or modified this entry<br><br>";
        }
        if(includedEndDate!=null){
            emailBody += eventForNotification.formatSummaryForNotification(includedEndDate);
        }else{
            emailBody += eventForNotification.formatSummaryForNotification();
        }
        if(eventForNotification.getEventGroup()!=null && !ignoreReoccurr){
            //reoccurring event
            emailBody += "<br> - this entry reoccurs!";
        }

        emailBody += "</p>";
        emailService.sendMailWithHtmlBody(emailTo, emailSubject, emailBody);
    }


    public void checkForConflict(EntryTimeChangedEvent e, CalendarView schedulerView) {
        Boolean allDaySlotUsed = null;
        editType = Scheduler.EditType.CALENDAR;
        //needs to return info about conflict event for display
        //if()
        ResourceEntry oldResourceEntry = (ResourceEntry) e.getEntry();
        log.info("checkForConflict: oldResourceEntry:" + oldResourceEntry);
        ResourceEntry newResourceEntry = (ResourceEntry) e.applyChangesOnEntry();
        log.info("checkForConflict: newResourceEntry:" + newResourceEntry);

        allDaySlotUsed = newResourceEntry.isAllDay();
        log.info("checkForConflict: oldFullDay:" + e.getEntry().isAllDay() + " newFullDay:" + newResourceEntry.isAllDay());

        String targetResourceId = newResourceEntry.getResource().get().getId();
        LocalDateTime targetStart = newResourceEntry.getStart();
        LocalDateTime targetEnd = newResourceEntry.getEnd();
        LocalDateTime targetStartWithOffset = newResourceEntry.getStartWithOffset();
        log.info("*** 1. targetStartWithOffset:" + targetStartWithOffset);
        LocalDateTime targetEndWithOffset = newResourceEntry.getEndWithOffset();
        String sourceId = newResourceEntry.getId();

        Boolean forceAllDay = Boolean.TRUE;
        if(schedulerView.equals(SchedulerView.RESOURCE_TIMELINE_WEEK) || schedulerView.equals(SchedulerView.RESOURCE_TIMELINE_MONTH) || schedulerView.equals(CalendarViewImpl.LIST_WEEK)){
            forceAllDay = Boolean.TRUE;
            //these views do not have an AllDaySlot so set to null
            allDaySlotUsed = null;
        }else{
            forceAllDay = Boolean.FALSE;
        }

        Optional<SchedulerEvent> eventToCheck = schedulerEventRepository.findById(Long.valueOf(sourceId));
        if(eventToCheck==null){
            log.info("checkForConflict: forceAllDay:" + forceAllDay + " sourceId:" + sourceId + " not found in the database");
            return;
        }
        if(forceAllDay || eventToCheck.get().getFullDay()){
            targetStartWithOffset = LocalDateTime.of(targetStart.toLocalDate(), eventToCheck.get().getStart().toLocalTime());
            log.info("*** 2. targetStartWithOffset:" + targetStartWithOffset);
            targetEndWithOffset = LocalDateTime.of(targetStart.toLocalDate(), eventToCheck.get().getEnd().toLocalTime());

        }else{
            //do not allow a SHIFT type entry to be moved to all day slot
            if(allDaySlotUsed && eventToCheck.get().getType().equals(Scheduler.EventType.SHIFT)){
                UIUtilities.showNotificationError("SHIFT type entry cannot be dropped on All Day slot.  Use edit instead.");
                log.info("checkForConflict: drop of SHIFT on All Day slot prevented");
                notifyRefreshNeeded();
                return;
            }
        }
        log.info("checkForConflict: forceAllDay:" + forceAllDay + " sourceId:" + sourceId + " targetResourceId:" + targetResourceId + " targetStartWithOffset:" + targetStartWithOffset);

        log.info("*** 3. targetStartWithOffset:" + targetStartWithOffset);
        allowSave(sourceId, targetResourceId, targetStart, targetEnd, targetStartWithOffset, targetEndWithOffset, forceAllDay, allDaySlotUsed, eventToCheck.get().getTeamId());
    }

    private void allowSave(String sourceId, String targetResourceId, LocalDateTime targetStart, LocalDateTime targetEnd, LocalDateTime targetStartWithOffset, LocalDateTime targetEndWithOffset, Boolean forceAllDay, Boolean allDaySlotUsed, Long teamId){
        Boolean conflicts = Boolean.FALSE;
        log.info("allowSave: sourceId:" + sourceId + " targetResourceId:" + targetResourceId + " targetStartWithOffset:" + targetStartWithOffset);
        List<SchedulerEvent> resourceEvents = schedulerEventRepository.findByResourceIdAndStartBetween(targetResourceId,targetStartWithOffset.toLocalDate().atStartOfDay(),targetStartWithOffset.toLocalDate().atTime(23,59),teamId);
        if(resourceEvents==null || resourceEvents.size()==0){
            log.info("allowSave: resourceEvents was null or size was 0");
        }else{
            //review conflicts
            log.info("allowSave: source:" + sourceId + " startWithOffset:" + targetStartWithOffset + " endWithOffset:" + targetEndWithOffset);
            for (SchedulerEvent schedulerEvent : resourceEvents) {
                log.info("allowSave: checking against:" + schedulerEvent.getId() + " start:" + schedulerEvent.getStart() + " end:" + schedulerEvent.getEnd());
                if((schedulerEvent.getEnd() == null || targetStartWithOffset.isBefore(schedulerEvent.getEnd())) && (targetEndWithOffset == null || targetEndWithOffset.isAfter(schedulerEvent.getStart()))){
                    if(sourceId!=null && sourceId.equals(schedulerEvent.getId().toString())){
                        log.info("allowSave: ignoring original event:" + schedulerEvent.getId() + " sourceId:" + sourceId);
                    }else{
                        log.info("allowSave: found conflict:" + schedulerEvent.getId() + " sourceId:" + sourceId);
                        conflicts = Boolean.TRUE;
                        break;
                    }
                }
            }
        }

        if(conflicts){
            ConfirmDialog confirmDialogBox = new ConfirmDialog();
            confirmDialogBox.setHeader("Allow conflict");
            confirmDialogBox.setText(
                    "Do you want to allow this event to conflict with existing event?");

            confirmDialogBox.setCancelable(true);
            confirmDialogBox.addCancelListener(event -> {
                log.info("allowSave: conflict found - not saving (Cancel)");
                conflictRefresh();
            });

            confirmDialogBox.setRejectable(true);
            confirmDialogBox.setRejectText("Discard save");
            confirmDialogBox.addRejectListener(event -> {
                log.info("allowSave: conflict found - not saving (Reject)");
                conflictRefresh();
            });

            confirmDialogBox.setConfirmText("Allow");
            confirmDialogBox.addConfirmListener(event -> {
                log.info("allowSave: conflict found but saving");
                conflictSave(sourceId,targetResourceId,targetStart,targetEnd,forceAllDay, allDaySlotUsed);
            });

            confirmDialogBox.open();
        }else{
            log.info("allowSave: No conflict found or skipped");
            if(editType.equals(Scheduler.EditType.CALENDAR)){
                saveCalendarEditedEvent(sourceId,targetResourceId,targetStart,targetEnd, forceAllDay, allDaySlotUsed);
            }else{
                saveDialogEditedEvent(sourceId,targetResourceId,targetStart,targetEnd, forceAllDay);
            }
        }
    }

    private void conflictSave(String sourceId, String targetResourceId, LocalDateTime targetStart, LocalDateTime targetEnd, Boolean forceAllDay, Boolean allDaySlotUsed){
        if(editType.equals(Scheduler.EditType.CALENDAR)){
            log.info("conflictSave: CALENDAR: allow save passed: calling saveEditedEvent");
            saveCalendarEditedEvent(sourceId,targetResourceId,targetStart,targetEnd, forceAllDay, allDaySlotUsed);
        }else{
            log.info("conflictSave: DIALOG: allow save passed: calling saveEditedEvent");
            saveDialogEditedEvent(sourceId,targetResourceId,targetStart,targetEnd, forceAllDay);
        }
    }

    private void conflictRefresh(){
        if(editType.equals(Scheduler.EditType.CALENDAR)){
            log.info("conflictRefresh: CALENDAR: calling refresh");
            //TODO: this is NOT undoing the edit
            notifyRefreshNeeded();
        }else{
            log.info("conflictRefresh: DIALOG: calling validate and returning to Dialog");
            dialogValidate();
        }
    }

    private void saveCalendarEditedEvent(String id, String resourceId, LocalDateTime start, LocalDateTime end, Boolean allDayView, Boolean allDaySlotUsed ){
        log.info("saveCalendarEditedEvent: save changes: start:" + start + " end:" + end);
        Optional<SchedulerEvent> eventToEdit = schedulerEventRepository.findById(Long.valueOf(id));



        if(eventToEdit==null){
            log.info("saveCalendarEditedEvent: save FAILED: event id not found for:" + id);
        }else{
            if(eventToEdit.get().getEventGroup()!=null){
                UIUtilities.showNotification("Cannot drag/drop a reoccurring entry. Select entry and edit using the dialog!");
                //confirmEditReoccurItem(eventToEdit.get());
            }else{
                if(allDayView || eventToEdit.get().getFullDay()){
                    log.info("saveCalendarEditedEvent: AllDayView: update fields started");
                    eventToEdit.get().setStart(LocalDateTime.of(start.toLocalDate(),eventToEdit.get().getStart().toLocalTime()));
                    eventToEdit.get().setEnd(LocalDateTime.of(start.toLocalDate(),eventToEdit.get().getEnd().toLocalTime()));
                }else{
                    log.info("saveCalendarEditedEvent: Other: update fields started: start:" + Scheduler.tzDefault.applyTimezoneOffset(start) + " end:" + Scheduler.tzDefault.applyTimezoneOffset(end));
                    eventToEdit.get().setStart(Scheduler.tzDefault.applyTimezoneOffset(start));
                    eventToEdit.get().setEnd(Scheduler.tzDefault.applyTimezoneOffset(end));
                }
                eventToEdit.get().setResourceId(resourceId);
                //check to see if we need to change the duration type
                if(allDaySlotUsed==null){
                    //do not change anything
                    log.info("saveCalendarEditedEvent: allDaySlotUsed was null - no change");
                }else if(allDaySlotUsed){
                    log.info("saveCalendarEditedEvent: allDaySlotUsed true. Force duration to full day");
                    eventToEdit.get().setFullDay(true);
                    eventToEdit.get().setStart(LocalDateTime.of(start.toLocalDate(), Scheduler.minTime));
                    eventToEdit.get().setEnd(LocalDateTime.of(start.toLocalDate(), Scheduler.maxTime));
                }else{
                    log.info("saveCalendarEditedEvent: allDaySlotUsed false. Force duration to partial day: start:" + start + " end:" + end);
                    eventToEdit.get().setFullDay(false);
                    eventToEdit.get().setStart(Scheduler.tzDefault.applyTimezoneOffset(start));
                    eventToEdit.get().setEnd(Scheduler.tzDefault.applyTimezoneOffset(end));
                }
                //set the event to NOT published so it will get published later
                eventToEdit.get().setPublished(false);

                schedulerEventRepository.save(eventToEdit.get());
                log.info("saveCalendarEditedEvent: save performed");
            }

        }
        notifyRefreshNeeded();
    }

    private void confirmEditReoccurItem(SchedulerEvent schedulerEvent, String originalResourceId) {
        String changeString;
        if(dialogMode.equals(DialogMode.DELETE)){
            changeString = "delete";
        }else{ //EDIT
            changeString = "modify";
        }

        ConfirmDialog dialogConfirm = new ConfirmDialog();
        dialogConfirm.setHeader("Confirm change to reoccurring entry?");
        String note = "<br><br>* Note: modifying a single entry will remove it from the reoccurrence!";
        Html text = new Html("<p>Are you sure you want to " + changeString + " this and all future reoccurring entries?<br>Original Entry:<br>" + schedulerEvent.getDescription() + note + "</p>");
        dialogConfirm.setText(text);

        dialogConfirm.setCancelable(true);
        //dialog.addCancelListener(event -> setStatus("Canceled"));

        dialogConfirm.setRejectable(true);
        dialogConfirm.setRejectText(changeString.toUpperCase() + " + all future");
        dialogConfirm.addRejectListener(event -> {
            //get list of all future entries including this one
            List<SchedulerEvent> schedulerEvents = schedulerEventRepository.findByEventGroup_IdAndStartGreaterThanEqual(schedulerEvent.getEventGroup().getId(),schedulerEvent.getStart());
            log.info("confirmEditReoccurItem: future entries:" + schedulerEvents.size());

            Integer eventCounter = 0;
            for (SchedulerEvent schedulerEvent1: schedulerEvents) {
                eventCounter++;
                if(dialogMode.equals(DialogMode.DELETE)){
                    log.info("confirmEditReoccurItem: deleting:" + schedulerEvent1);
                    if(isOnlyUser && eventCounter.equals(1)){
                        sendNotificationToAdmin(driver.getName(), schedulerEvent1, Boolean.TRUE, Boolean.FALSE, driver.getTeamName());
                    }
                    schedulerEventRepository.delete(schedulerEvent1);
                }else{
                    updateEventDetails(schedulerEvent1);
                    if(eventCounter.equals(1)){ //only notify once
                        if(isOnlyUser){
                            sendNotificationToAdmin(driver.getName(), schedulerEvent1, Boolean.FALSE, Boolean.FALSE,driver.getTeamName());
                        }else{
                            if(dialogMode.equals(DialogMode.EDIT) && schedulerEvent1.getType().equals(Scheduler.EventType.SHIFT)){
                                if(schedulerEvent1.getPublished()){
                                    //if already published then need to notify driver of change
                                    publishedShiftNotifications(this.event,originalResourceId, schedulerEvent1.getResourceId());
                                } //else notification will go when published
                            }
                        }
                    }
                    log.info("confirmEditReoccurItem: saving:" + schedulerEvent1);
                    schedulerEventRepository.save(schedulerEvent1);
                }
            }
            if(dialogMode.equals(DialogMode.DELETE)){
                cleanupEventGroup(schedulerEvent.getEventGroup().getId());
            }
            dialog.close();
            notifyRefreshNeeded();
        });

        dialogConfirm.setConfirmText("Only this entry");
        //dialogConfirm.setConfirmButtonTheme("error primary");
        dialogConfirm.addConfirmListener(event -> {
            if(dialogMode.equals(DialogMode.DELETE)){
                if(isOnlyUser){
                    sendNotificationToAdmin(driver.getName(), schedulerEvent, Boolean.TRUE, Boolean.TRUE, driver.getTeamName());
                }
                schedulerEventRepository.delete(schedulerEvent);
                cleanupEventGroup(schedulerEvent.getEventGroup().getId());
            }else{
                //remove this entry from the reoccur group
                schedulerEvent.setEventGroup(null);
                updateEventDetails(schedulerEvent);
                log.info("confirmEditReoccurItem: saving:" + schedulerEvent);
                if(isOnlyUser){
                    sendNotificationToAdmin(driver.getName(), schedulerEvent, Boolean.FALSE, Boolean.TRUE, driver.getTeamName());
                }else{
                    if(dialogMode.equals(DialogMode.EDIT) && schedulerEvent.getType().equals(Scheduler.EventType.SHIFT)){
                        if(schedulerEvent.getPublished()){
                            //if already published then need to notify driver of change
                            publishedShiftNotifications(this.event,originalResourceId, schedulerEvent.getResourceId());
                        } //else notification will go when published
                    }
                }
                schedulerEventRepository.save(schedulerEvent);
            }
            dialog.close();
            notifyRefreshNeeded();
        });
        dialogConfirm.open();
    }

    private void cleanupEventGroup(Long eventGroupId){
        //check if there are ZERO entries with this eventGroupId and then delete
        List<SchedulerEvent> checkEvents = schedulerEventRepository.findByEventGroup_Id(eventGroupId);
        if(checkEvents==null || checkEvents.size()==0){
            //delete the group record itself
            log.info("cleanupEventGroup: deleting eventGroupId:" + eventGroupId);
            schedulerEventGroupRepository.deleteById(eventGroupId);
        }else{
            log.info("cleanupEventGroup: " + checkEvents.size() + " entries found. Not deleting eventGroupId:" + eventGroupId);
        }
    }

    private void updateEventDetails(SchedulerEvent schedulerEvent){
        LocalDate occurDate = schedulerEvent.getStart().toLocalDate();
        if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY)){
            log.info("updateEventDetails: FullDay: occurDate:" + occurDate);
            schedulerEvent.setFullDay(true);
            //make sure the start and end are set to the start and end of the day
            schedulerEvent.setStart(LocalDateTime.of(occurDate, Scheduler.minTime));
            schedulerEvent.setEnd(LocalDateTime.of(occurDate, Scheduler.maxTime));
        }else{
            log.info("updateEventDetails: occurDate:" + occurDate);
            schedulerEvent.setFullDay(false);
            schedulerEvent.setStart(LocalDateTime.of(occurDate, dialogStartTime.getValue()));
            schedulerEvent.setEnd(LocalDateTime.of(occurDate, dialogEndTime.getValue()));
        }
        String targetResourceId;
        if(dialogDriver.isEmpty()){
            targetResourceId = Scheduler.availableShiftsResourceId;
        }else{
            targetResourceId = dialogDriver.getValue().getFleetId().toString();
        }
        schedulerEvent.setResourceId(targetResourceId);
        schedulerEvent.setType(dialogEventType.getValue());
        //edited event should be unpublished
        //schedulerEvent.setPublished(Boolean.FALSE);

    }

    public void addListener(SchedulerRefreshNeededListener listener){
        schedulerRefreshNeededListeners.add(listener);
    }

    private void notifyRefreshNeeded(){
        for (SchedulerRefreshNeededListener listener: schedulerRefreshNeededListeners) {
            listener.schedulerRefreshNeeded();
        }
    }

    private VerticalLayout dialogLayout() {

        //dialogEventType.setLabel("-Type");
        dialogEventType.setItems(Scheduler.EventType.values());
        dialogEventType.setItemLabelGenerator(type -> {
            return type.typeName;
        });
        dialogEventType.addValueChangeListener(e -> {
            if(validationEnabled){
                //this.event.setType(e.getValue());
                updateEventType();
                log.info("dialogLayout: value changed from:" + e.getOldValue() + " to:" + e.getValue());
            }
        });

        //dialogDriver.setItems(driversRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        dialogDriver.setLabel("-Driver");
        dialogDriver.setItemLabelGenerator(driver -> {
            if (driver == null) {
                return Scheduler.availableShiftsDisplayName;
            }
            return driver.getName();
        });
        dialogDriver.setReadOnly(true);
        dialogDriver.setPlaceholder("Select driver");
        dialogDriver.setEmptySelectionAllowed(true);
        dialogDriver.setEmptySelectionCaption(Scheduler.availableShiftsDisplayName);
        dialogDriver.addValueChangeListener(item -> {
            if(validationEnabled){
                //this.event.setResourceId(item.getValue().getFleetId().toString());
                dialogValidate();
            }
        });
        dialogDriver.addThemeVariants(SelectVariant.LUMO_SMALL);

        dialogStartDate.setLabel("-Date");
        dialogStartDate.setReadOnly(true);
        dialogStartDate.addThemeVariants(DatePickerVariant.LUMO_SMALL);
        dialogStartDate.addValueChangeListener(e -> {
            if(validationEnabled) updateDialogTimes();
        });

        dialogEndDate.setLabel("- End Date");
        dialogEndDate.setReadOnly(true);
        dialogEndDate.addThemeVariants(DatePickerVariant.LUMO_SMALL);
        dialogEndDate.setVisible(false);
        dialogEndDate.addValueChangeListener(e -> {
            if(validationEnabled) updateDialogTimes();
        });

        Divider divider = new Divider();

        dialogDurationType.setItems(Scheduler.EventDurationType.values());
        dialogDurationType.setItemLabelGenerator(type -> {
            return type.typeName;
        });
        dialogDurationType.addValueChangeListener(e -> {
            if(validationEnabled){
                updateDurationType();
                log.info("dialogLayout: duration type value changed from:" + e.getOldValue() + " to:" + e.getValue());
            }
        });

        dialogTimeSelection.setVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        dialogStartTime.setLabel("-Start");
        dialogStartTime.setMin(Scheduler.minTimeDialog);
        dialogStartTime.setMax(Scheduler.maxTimeDialog);
        dialogStartTime.setStep(Scheduler.timeStep);
        dialogStartTime.setWidth("120px");
        dialogStartTime.addThemeVariants(TimePickerVariant.LUMO_SMALL);
        dialogStartTime.addValueChangeListener(e -> {
            dialogEndTime.setMin(e.getValue().plusMinutes(30));
            if(validationEnabled) updateDialogTimes();
        });
        dialogEndTime.setLabel("-End");
        dialogEndTime.setMin(Scheduler.minTimeDialog);
        dialogEndTime.setMax(Scheduler.maxTimeDialog);
        dialogEndTime.setStep(Scheduler.timeStep);
        dialogEndTime.setWidth("120px");
        dialogEndTime.addThemeVariants(TimePickerVariant.LUMO_SMALL);
        dialogEndTime.addValueChangeListener(e -> {
            dialogStartTime.setMax(e.getValue().minusMinutes(30));
            if(validationEnabled) updateDialogTimes();
        });
        dialogHours.setWidth("60px");
        dialogTimeSelection.add(dialogStartTime,dialogEndTime,dialogHours);

        dialogReoccur.setLabel("Reoccur");
        dialogReoccur.setEnabled(false);
        dialogReoccur.addValueChangeListener(item -> {
            if(validationEnabled){
                updateReoccurFields();
            }
        });

        dialogReoccurSelection.setVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        dialogReoccurInterval.setLabel("Interval");
        dialogReoccurInterval.setEnabled(false);
        dialogReoccurInterval.setVisible(false);
        dialogReoccurInterval.setMin(1);
        dialogReoccurInterval.setMax(4);
        dialogReoccurInterval.setStepButtonsVisible(true);
        dialogReoccurInterval.addValueChangeListener(item -> {
            updateReoccurFields();
        });
        dialogReoccurUntil.setLabel("Until");
        dialogReoccurUntil.setEnabled(false);
        dialogReoccurUntil.setVisible(false);

        dialogReoccurSelection.add(dialogReoccurInterval, dialogReoccurUntil);

        //Not changable from dialog - must perform a publish
        dialogPublished.setLabel("Published");
        dialogPublished.setEnabled(false);

        VerticalLayout fieldLayout = new VerticalLayout(dialogEventType,dialogDriver, dialogStartDate, dialogEndDate, new Divider(), dialogDurationType, dialogTimeSelection,dialogReoccur,dialogReoccurSelection, new Divider(), dialogPublished);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        return fieldLayout;
    }

    private void updateReoccurFields() {
        dialogReoccurSelection.setVisible(dialogReoccur.getValue());
        dialogReoccurInterval.setEnabled(dialogReoccur.getValue());
        dialogReoccurInterval.setVisible(dialogReoccur.getValue());
        dialogReoccurUntil.setEnabled(dialogReoccur.getValue());
        dialogReoccurUntil.setVisible(dialogReoccur.getValue());
        if(dialogReoccur.getValue()){
            /*
            String intervalString;
            if(dialogReoccurInterval.getValue().equals(2)){
                intervalString = "Reoccur every 2nd ";
            }else if(dialogReoccurInterval.getValue().equals(3)){
                intervalString = "Reoccur every 3rd ";
            }else if(dialogReoccurInterval.getValue().equals(4)){
                intervalString = "Reoccur every 4th ";
            }else{
                intervalString = "Reoccur every ";
            }

             */
            dialogReoccurUntil.setValue(dialogStartDate.getValue().plusYears(1L));
            dialogReoccurUntil.setMin(dialogStartDate.getValue());
            dialogReoccurUntil.setMax(dialogStartDate.getValue().plusYears(1L));
            dialogReoccur.setLabel(Scheduler.getIntervalString(dialogReoccurInterval.getValue()) + dialogStartDate.getValue().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()));
        }else{
            dialogReoccur.setLabel("Reoccurrence (None)");
        }
    }

    private void updateEventType() {
        //based on the current value of EventType dialog field
        if(dialogEventType.getValue().equals(Scheduler.EventType.SHIFT)){
            dialogDurationType.setValue(Scheduler.EventDurationType.PARTIALDAY);
            dialogDurationType.setEnabled(false);
            dialogReoccur.setEnabled(true);
            dialogReoccur.setReadOnly(false);
        }else{
            //UNAVAILABLE and OFF allow full day
            dialogDurationType.setEnabled(true);
            if(dialogEventType.getValue().equals(Scheduler.EventType.OFF)){
                dialogReoccur.setValue(false);
                dialogReoccur.setEnabled(false);
                dialogReoccur.setReadOnly(true);
            }else{
                dialogReoccur.setEnabled(true);
                dialogReoccur.setReadOnly(false);
            }
        }
        updateEndDate();
        dialogValidate();
    }

    private void updateEndDate(){
        if(dialogEventType.getValue().equals(Scheduler.EventType.OFF) && dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY)){
            dialogEndDate.setVisible(true);
            dialogEndDate.setReadOnly(false);
            dialogEndDate.setEnabled(true);
        }else{
            dialogEndDate.setVisible(false);
            dialogEndDate.setReadOnly(true);
            dialogEndDate.setEnabled(false);
        }
    }

    private void updateDurationType() {
        //based on the current value of FullDay dialog field
        if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY)){
            dialogTimeSelection.setVisible(false);
        }else{
            dialogTimeSelection.setVisible(true);
        }
        updateEndDate();
        dialogValidate();
    }

    private void updateDialogTimes() {
        updateReoccurFields();
        dialogHours.setValue(getHours());
        dialogEndDate.setMin(dialogStartDate.getValue());
        dialogValidate();
    }

    private String getHours(){
        LocalDateTime start = LocalDateTime.of(dialogStartDate.getValue(),dialogStartTime.getValue());
        LocalDateTime end = LocalDateTime.of(dialogStartDate.getValue(),dialogEndTime.getValue());
        Duration dur = Duration.between(start, end);
        long millis = dur.toMillis();

        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)));
    }


    public void dialogOpen(SchedulerEvent event, Boolean isOnlyUser, Long signedInDriverId){
        this.isOnlyUser = isOnlyUser;
        this.signedInDriverId = signedInDriverId;
        validationEnabled = Boolean.FALSE;
        editType = Scheduler.EditType.DIALOG;
        driver = null;
        if(event==null){ //invalid event
            log.info("dialogOpen: null event passed");
            return;
        }else{
            this.event = event;
            if(this.event.getResourceId().equals(Scheduler.availableShiftsResourceId)){
                //leave driver as null = unassigned
            }else{
                log.info("dialogOpen: lookup driver by id:" + this.event.getResourceId());
                Long driverID = Long.valueOf(this.event.getResourceId());
                if(driverID!=null){
                    driver = driversRepository.findDriverByFleetId(driverID);
                }
                log.info("dialogOpen: AFTER lookup driver by id:" + this.event.getResourceId() + " driver:" + driver);
            }
        }

        setValues();

        //set EventType default
        setEventTypeList(List.of(Scheduler.EventType.values()));

        if(this.isOnlyUser){
            //Driver user
            if(dialogMode.equals(DialogMode.NEW)){
                setDialogReadOnly(false);
                dialogDriver.setReadOnly(true);
                setEventTypeList(listNoShift);
            }else{
                if(driver==null){
                    setDialogReadOnly(true);
                }else if(driver.getFleetId().equals(signedInDriverId)){
                    //event for current signed in driver - allow edit for Off and Not Available
                    if(this.event.getType().equals(Scheduler.EventType.SHIFT)){
                        //read only
                        setDialogReadOnly(true);
                    }else{
                        //allow create and edit for OFF and NA
                        setEventTypeList(listNoShift);
                        setDialogReadOnly(false);
                    }
                }else{
                    //readonly
                    setDialogReadOnly(true);
                }
            }
        }else{
            //Admin user - allow new/edit for all
            setDialogReadOnly(false);

            if(dialogMode.equals(DialogMode.EDIT) && !this.event.getPublished() && this.event.getType().equals(Scheduler.EventType.OFF)){
                dialogPublishButton.setVisible(true);
                dialogPublishButton.setEnabled(true);
            }else{
                dialogPublishButton.setVisible(false);
                dialogPublishButton.setEnabled(false);
            }
        }

        validationEnabled = Boolean.TRUE;

            //validate
        dialogValidate();
        dialog.open();
    }

    private void setEventTypeList(List<Scheduler.EventType> list){
        dialogEventType.setItems(list);
        if(dialogMode.equals(DialogMode.NEW)){
            if(isOnlyUser){
                dialogEventType.setValue(Scheduler.EventType.UNAVAILABLE);
            }else{
                dialogEventType.setValue(Scheduler.EventType.SHIFT);
            }
        }else{
            dialogEventType.setValue(event.getType());
        }
        updateEventType();
    }

    private void setDialogReadOnly(Boolean readOnlyMode){
        dialogDriver.setReadOnly(readOnlyMode);
        dialogStartDate.setReadOnly(readOnlyMode);
        dialogEndDate.setReadOnly(readOnlyMode);
        dialogDurationType.setReadOnly(readOnlyMode);
        dialogStartTime.setReadOnly(readOnlyMode);
        dialogEndTime.setReadOnly(readOnlyMode);

        //always readonly
        dialogPublished.setReadOnly(true);

        if(dialogMode.equals(DialogMode.NEW)){
            dialogEventType.setReadOnly(readOnlyMode);
            dialogDeleteButton.setEnabled(false);
            dialogDeleteButton.setVisible(false);
            dialogOkButton.setEnabled(true);
            dialogOkButton.setVisible(true);
            dialog.setHeaderTitle("Create schedule entry");
            dialogReoccur.setReadOnly(readOnlyMode);
            dialogReoccur.setEnabled(!readOnlyMode);
            dialogReoccurInterval.setReadOnly(readOnlyMode);
            dialogReoccurUntil.setReadOnly(readOnlyMode);
            dialogReoccurUntil.setMin(dialogStartDate.getValue());
            dialogReoccurUntil.setMax(dialogStartDate.getValue().plusYears(1L));
        }else { //View/Edit/Delete
            dialogEventType.setReadOnly(true);
            if(readOnlyMode){
                dialogDeleteButton.setEnabled(false);
                dialogDeleteButton.setVisible(false);
                dialogOkButton.setEnabled(false);
                dialogOkButton.setVisible(false);
            }else{
                dialogDeleteButton.setEnabled(true);
                dialogDeleteButton.setVisible(true);
                dialogOkButton.setEnabled(true);
                dialogOkButton.setVisible(true);
            }

            dialog.setHeaderTitle("View/Edit schedule entry");

            //reoccur fields always readonly
            dialogReoccur.setReadOnly(true);
            dialogReoccur.setEnabled(false);
            dialogReoccurInterval.setReadOnly(true);
            dialogReoccurUntil.setReadOnly(true);
        }

    }

    private void setValues(){
        //set values
        dialogDriver.setItems(driversRepository.findActiveByTeamOrderByNameAsc(event.getTeamId()));
        dialogDriver.setValue(driver);

        dialogEventType.setValue(event.getType());
        updateEventType();

        dialogStartDate.setValue(this.event.getStart().toLocalDate());

        dialogEndDate.setValue(this.event.getEnd().toLocalDate());

        log.info("setValues: fullDay:" + event.getFullDay());
        if(event.getFullDay()){
            dialogDurationType.setValue(Scheduler.EventDurationType.FULLDAY);
        }else{
            dialogDurationType.setValue(Scheduler.EventDurationType.PARTIALDAY);
        }
        updateDurationType();

        dialogStartTime.setValue(this.event.getStart().toLocalTime());
        dialogEndTime.setValue(this.event.getEnd().toLocalTime());
        dialogHours.setValue(this.event.getHours());

        if(this.event.getEventGroup()==null){
            dialogReoccur.setValue(Boolean.FALSE);
            dialogReoccurInterval.setValue(1);
            dialogReoccurUntil.setValue(dialogStartDate.getValue().plusYears(1L));
        }else{
            dialogReoccur.setValue(Boolean.TRUE);
            dialogReoccurInterval.setValue(event.getEventGroup().getReoccurInterval());
            dialogReoccurUntil.setValue(event.getEventGroup().getReoccurUntil());
        }
        updateReoccurFields();

        dialogPublished.setValue(this.event.getPublished());
    }

    private void dialogValidate() {
        if(validationEnabled && this.event!=null){
            if(dialogMode.equals(DialogMode.NEW)){
                hasChangedValues = Boolean.TRUE;
            }else{
                hasChangedValues = Boolean.FALSE;
                validateDriver();
                validateRadioButtonGroup(dialogEventType, event.getType().toString());
                validateDateField(dialogStartDate, event.getStart().toLocalDate());
                if(dialogDurationType.getValue().equals(Scheduler.EventDurationType.FULLDAY) && dialogEventType.getValue().equals(Scheduler.EventType.OFF)){
                    validateDateField(dialogEndDate, event.getEnd().toLocalDate());
                }
                Scheduler.EventDurationType durationType;
                if(event.getFullDay()){
                    durationType = Scheduler.EventDurationType.FULLDAY;
                }else{
                    durationType = Scheduler.EventDurationType.PARTIALDAY;
                }
                validateRadioButtonGroup(dialogDurationType,durationType.toString());
                validateTimeField(dialogStartTime, event.getStart().toLocalTime());
                validateTimeField(dialogEndTime, event.getEnd().toLocalTime());
                if(dialogReoccur.getValue()){
                    validateIntegerField(dialogReoccurInterval, event.getEventGroup().getReoccurInterval());
                    validateDateField(dialogReoccurUntil, event.getEventGroup().getReoccurUntil());
                }
            }
        }
        log.info("dialogValidate: hasChangedValues:" + hasChangedValues);
        if(hasChangedValues){
            enableOkReset(true);
        }else{
            enableOkReset(false);
        }

    }

    private void validateIntegerField(IntegerField field, Integer value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateTimeField(TimePicker field, LocalTime value){
        if(value==null && field.getValue()==null){
            field.getStyle().set("box-shadow","none");
        }else if(value==null && field.getValue()!=null){
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }else if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateDateField(DatePicker field, LocalDate value){
        if(value==null && field.getValue()==null){
            field.getStyle().set("box-shadow","none");
        }else if(value==null && field.getValue()!=null){
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }else if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateCheckbox(Checkbox field, Boolean value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateRadioButtonGroup(RadioButtonGroup field, String value){
        log.info("validateRadioButtonGroup: field toString:" + field.getValue() + " value:" + value);
        if(field.getValue().toString().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateDriver(){
        String value = event.getResourceId();
        String field = null;
        if(dialogDriver.getValue()!=null){
            field = dialogDriver.getValue().getFleetId().toString();
        }
        log.info("validateDriver: field:" + field + " value:" + value);
        if((value==null && field==null) || (value.equals("0") && field==null)){
            dialogDriver.getStyle().set("box-shadow","none");
        }else if(value==null && field!=null || value!=null && field==null){
            dialogDriver.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            dialogDriver.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }else if(field.equals(value)){
            dialogDriver.getStyle().set("box-shadow","none");
        }else{
            dialogDriver.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            dialogDriver.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }



    public DialogMode getDialogMode() {
        return dialogMode;
    }

    public void setDialogMode(DialogMode dialogMode) {
        this.dialogMode = dialogMode;
    }

    public Scheduler.EditType getEditType() {
        return editType;
    }

    public void setEditType(Scheduler.EditType editType) {
        this.editType = editType;
    }
}
