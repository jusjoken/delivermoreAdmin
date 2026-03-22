package ca.admin.delivermore.views.drivers;

import ca.admin.delivermore.collector.data.Config;
import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.components.custom.FullCalendarWithTooltip;
import ca.admin.delivermore.components.custom.LocationChoice;
import ca.admin.delivermore.components.custom.LocationChoiceChangedListener;
import ca.admin.delivermore.components.custom.SchedulerMenuBar;
import ca.admin.delivermore.data.scheduler.*;
import ca.admin.delivermore.data.scheduler.Scheduler;
import ca.admin.delivermore.data.service.SchedulerService;
import ca.admin.delivermore.security.AuthenticatedUser;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.stefan.fullcalendar.*;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@PageTitle("Schedule")
@Route(value = "schedule", layout = MainLayout.class)
@RolesAllowed("USER")
public class ScheduleView extends Main implements SchedulerRefreshNeededListener, LocationChoiceChangedListener {

    private Logger log = LoggerFactory.getLogger(ScheduleView.class);
    LocalDate startDate;
    LocalDate endDate;
    private VerticalLayout detailsLayout = UIUtilities.getVerticalLayout();
    private FullCalendarScheduler scheduler;
    private SchedulerMenuBar schedulerMenuBar;
    private Scheduler.SchedulerType schedulerType = Scheduler.SchedulerType.CALENDAR;

    private CalendarView currentSchedulerView;
    private Icon viewIcon;
    private Boolean viewChanged = Boolean.FALSE;
    private Boolean currentShowAllDrivers = Boolean.FALSE;

    private LocationChoice locationChoice = new LocationChoice(false);

    //private SchedulerInMemoryProvider entryProvider;
    private SchedulerService schedulerService = new SchedulerService();

    private Map<String, SchedulerResource> resourceMap = new TreeMap<>();

    private SchedulerEventDialog eventDialog = new SchedulerEventDialog();
    private Optional<Driver> signedInDriver;

    Long driverIdForView;
    private Boolean isOnlyUser = Boolean.TRUE;
    private AuthenticatedUser authenticatedUser;

    private EmailService emailService;
    private DriversRepository driversRepository;

    public ScheduleView(@Autowired AuthenticatedUser authenticatedUser, @Autowired EmailService emailService, @Autowired DriversRepository driversRepository) {
        this.authenticatedUser = authenticatedUser;
        this.emailService = emailService;
        this.driversRepository = driversRepository;
        signedInDriver = getSignedInDriver();
        if(signedInDriver.get()!=null && signedInDriver.get().isAdmin()){
            isOnlyUser = Boolean.FALSE;
            //set default scheduler type to use for the admin
            schedulerType = Scheduler.SchedulerType.CALENDAR;
            currentSchedulerView = SchedulerView.RESOURCE_TIMELINE_WEEK;
        }else{
            //set default scheduler type to use for a driver
            schedulerType = Scheduler.SchedulerType.LIST;
            currentSchedulerView = CalendarViewImpl.LIST_WEEK;
        }
        log.info("signedInDriver:" + signedInDriver);
        locationChoice.addListener(this);


        driverIdForView = signedInDriver.get().getFleetId();
        if(driverIdForView!=null){
            //set scheduler type to use
            String lastUsedView = Config.getInstance().getSetting(driverIdForView.toString(), Scheduler.driverLastUsedView);
            if(lastUsedView!=null){
                if(lastUsedView.equals(Scheduler.SchedulerType.LIST)){
                    schedulerType = Scheduler.SchedulerType.LIST;
                    currentSchedulerView = CalendarViewImpl.LIST_WEEK;
                }else{
                    schedulerType = Scheduler.SchedulerType.CALENDAR;
                    currentSchedulerView = SchedulerView.RESOURCE_TIMELINE_WEEK;
                }
            }

            //set the location to use
            if(isOnlyUser){
                locationChoice.setSelectedLocationId(signedInDriver.get().getTeamId());
            }else{
                Long lastUsedLocation = Config.getInstance().getSettingAsLong(driverIdForView.toString(),locationChoice.getLastLocatioinUsed(),locationChoice.getNoLocationsFound().getTeamId());
                locationChoice.setSelectedLocationId(lastUsedLocation);
            }
        }
        log.info("constructor: set schedulerType:" + schedulerType);

        startDate = LocalDate.now();
        log.info("ScheduleView: startDate:" + startDate);
        setSizeFull();
        detailsLayout.setSizeFull();
        buildSchedule();
        detailsLayout.add(getToolbar());
        add(detailsLayout);
        sizeScheduler();
        eventDialog.addListener(this);
    }

    private Optional<Driver> getSignedInDriver() {
        log.info("getSignedInDriver:" + authenticatedUser.get());
        return authenticatedUser.get();
    }

    private void sizeScheduler(){
        //add fix so the sidebar menu is not overwritten by the header of the calendar
        //removed as test 6.0.0  scheduler.addCustomStyles(".fc .fc-scrollgrid-section-sticky > * {z-index: 1;} ");
        //scheduler.setHeightByParent();
        scheduler.setWidthFull();
        detailsLayout.add(scheduler);
        detailsLayout.setFlexGrow(1, scheduler);
        detailsLayout.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);
        scheduler.setSizeFull();
        log.info("sizeScheduler: end");
    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = UIUtilities.getHorizontalLayout();

        toolbar.add(schedulerMenuBar);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");

        //email notification setting - default is enabled/true
        if(driverIdForView!=null){
            MenuItem settingsEmailNotifications;
            settingsEmailNotifications = schedulerMenuBar.getSettingsMenuItem().getSubMenu().addItem("Enable Email Notifications");
            settingsEmailNotifications.setCheckable(true);
            settingsEmailNotifications.setChecked(Config.getInstance().getEmailNotifications(driverIdForView.toString(), Boolean.TRUE));
            settingsEmailNotifications.addClickListener(e -> {
                Config.getInstance().setEmailNotifications(driverIdForView.toString(), !Config.getInstance().getEmailNotifications(driverIdForView.toString(), Boolean.TRUE));
            });
        }

        //add view toggle icon
        viewIcon = new Icon(VaadinIcon.CALENDAR_CLOCK);
        viewIcon.setTooltipText("Switch between calendar and list view");
        MenuItem tempItem = schedulerMenuBar.addItem(viewIcon);
        tempItem.setVisible(true);
        tempItem.setEnabled(true);
        viewIcon.addClickListener(item -> {
            toggleView();
        });

        Icon addIcon = new Icon(VaadinIcon.PLUS);
        addIcon.setColor("green");
        addIcon.setTooltipText("Add new entry");
        addIcon.addClickListener(item -> {
            eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.NEW);
            SchedulerEvent newEvent;
            newEvent = new SchedulerEvent(Scheduler.EventType.SHIFT, startDate.atTime(Scheduler.minTime), startDate.atTime(Scheduler.maxTime), signedInDriver.get().getFleetId().toString(), false,false, locationChoice.getSelectedLocationId());
            eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.NEW);
            eventDialog.dialogOpen(newEvent, isOnlyUser, signedInDriver.get().getFleetId());
        });
        schedulerMenuBar.addItem(addIcon);

        if(!isOnlyUser){  //Admin user
            Icon pubIcon = new Icon(VaadinIcon.CHECK);
            pubIcon.setColor("green");
            pubIcon.setTooltipText("Publish");
            pubIcon.addClickListener(item -> {
                publishCurrentSchedule();
            });
            schedulerMenuBar.addItem(pubIcon);
        }

        if(!isOnlyUser){
            toolbar.add(locationChoice.getMenuBar());
        }

        return toolbar;
    }

    private void toggleView(){
        if(schedulerType.equals(Scheduler.SchedulerType.CALENDAR)){
            schedulerType = Scheduler.SchedulerType.LIST;
        }else{
            schedulerType = Scheduler.SchedulerType.CALENDAR;
        }
        log.info("toggleView: view changed to:" + schedulerType);
        viewChanged = Boolean.TRUE;
        if(schedulerType.equals(Scheduler.SchedulerType.CALENDAR)){
            scheduler.changeView(SchedulerView.RESOURCE_TIMELINE_WEEK);
            currentSchedulerView = SchedulerView.RESOURCE_TIMELINE_WEEK;
        }else{
            scheduler.changeView(CalendarViewImpl.LIST_WEEK);
            currentSchedulerView = CalendarViewImpl.LIST_WEEK;
        }

        //save the new view as the last used view for the logged in driver
        Long driverId = signedInDriver.get().getFleetId();
        if(driverId!=null){
            Config.getInstance().setSetting(driverId.toString(), Scheduler.driverLastUsedView, schedulerType.name());
            log.info("toggleView: saving lastUsedView: schedulerType:" + schedulerType.name());
        }
        viewChanged = Boolean.FALSE;
        configureScheduler();

    }

    private void publishCurrentSchedule() {
        log.info("publishCurrentSchedule");
        //Note: endDate is 1 day too much by design of FC to be inclusive.  Need to subtract 1 day for SQL query
        List<SchedulerEvent> unpublishedEvents = schedulerService.getUnpublishedEntries(startDate,endDate.minusDays(1L), locationChoice.getSelectedLocationId());
        if(unpublishedEvents.size()==0){
            UIUtilities.showNotification("There are no unpublished entries to publish for current period.");
        }else{

            log.info("publishCurrentSchedule: confirm publish of unpublished events between " + startDate + " and " + endDate.minusDays(1L) + " for:" + locationChoice.getSelectedLocation());
            ConfirmDialog dialogConfirmPublish = new ConfirmDialog();
            dialogConfirmPublish.setHeader("Publish all for:" + locationChoice.getSelectedLocation() + "?" );
            Html text = new Html("<p>Are you sure you want to publish " + unpublishedEvents.size() + " unpublished entries for this period?<br></p>");
            dialogConfirmPublish.setText(text);

            dialogConfirmPublish.setCancelable(true);
            //dialog.addCancelListener(event -> setStatus("Canceled"));

            dialogConfirmPublish.setConfirmText("Publish");
            //dialogConfirmDelete.setConfirmButtonTheme("error primary");
            Map<String, List<SchedulerEvent>> publishedEventsMap = new HashMap<>();

            dialogConfirmPublish.addConfirmListener(event -> {
                List<Driver> activeDrivers = driversRepository.findActiveByTeamOrderByNameAsc(locationChoice.getSelectedLocationId());
                Map<Long,Driver> activeDriversMap = activeDrivers.stream().collect(Collectors.toMap(Driver::getFleetId, Function.identity()));
                for (SchedulerEvent schedulerEvent: unpublishedEvents) {
                    schedulerEvent.setPublished(true);
                    List<SchedulerEvent> eventsList = new ArrayList<>();
                    String thisResourceId = schedulerEvent.getResourceId();
                    //make sure this resource is active - otherwise make this event available
                    //log.info("publishCurrentSchedule: Checking driver exists: id:" + thisResourceId);
                    if(!thisResourceId.equals("0") && !activeDriversMap.containsKey(Long.valueOf(thisResourceId))){
                        log.info("publishCurrentSchedule: Driver " + thisResourceId + " does not exist as active driver: treating as available shift");
                        thisResourceId = "0";
                    }

                    if(publishedEventsMap.containsKey(thisResourceId)){
                        eventsList = publishedEventsMap.get(thisResourceId);
                        eventsList.add(schedulerEvent);
                        publishedEventsMap.replace(thisResourceId, eventsList);
                    }else{
                        eventsList.add(schedulerEvent);
                        publishedEventsMap.put(thisResourceId, eventsList);
                    }
                    schedulerService.saveEntry(schedulerEvent);
                }
                schedulerRefreshNeeded();
                String fromAddress = "tara.birch@delivermore.ca";
                for (String key: publishedEventsMap.keySet() ) {
                    log.info("publishCurrentSchedule: " + locationChoice.getSelectedLocation() + " DriverId:" + key);
                    //get driver info from key
                    String driverName = null;
                    String driverEmail = null;
                    Boolean availableShifts = Boolean.FALSE;
                    if(key.equals("0")){ //available shifts
                        availableShifts = Boolean.TRUE;
                        driverName = Scheduler.availableShiftsDisplayName;
                        for (Driver activeDriver: activeDrivers ) {
                            if(driverEmail==null){
                                driverEmail = activeDriver.getEmail();
                            }else{
                                driverEmail += ", " + activeDriver.getEmail();
                            }
                        }
                    }else{
                        Driver driver = driversRepository.findDriverByFleetId(Long.valueOf(key));
                        driverName = driver.getName();
                        driverEmail = driver.getEmail();
                    }
                    if(driverName!=null){
                        String subject = "DeliverMore schedule: " + Utility.dateRangeFormatted(startDate,endDate) + " for:" + driverName;
                        String htmlBody;
                        if(availableShifts){
                            htmlBody = "<p>A schedule has been published including the following " + Scheduler.availableShiftsDisplayName + "...<br><br>";
                        }else{
                            htmlBody = "<p>A schedule has been published including the following entries for you...<br><br>";
                        }
                        for (SchedulerEvent publishedEvent : publishedEventsMap.get(key) ) {
                            htmlBody += publishedEvent.formatSummaryForNotification() + "<br>";
                        }
                        htmlBody += "<br>Please review the full schedule for any changes at <a href='https://app.delivermore.ca'>DeliverMore App</a><br>";
                        htmlBody += "</p>";
                        emailService.sendMailWithHtmlBody(fromAddress,"", subject,htmlBody, driverEmail);
                        if(availableShifts){
                            UIUtilities.showNotification(Scheduler.availableShiftsDisplayName + " notification email sent to all " + locationChoice.getSelectedLocation() + " drivers at " + driverEmail);
                        }else{
                            UIUtilities.showNotification("Notification email sent to " + driverName + " at " + driverEmail);
                        }
                    }else{
                        log.info("publishCurrentSchedule: could not find driver for key:" + key);
                    }
                }

                UIUtilities.showNotification(unpublishedEvents.size() + " entries have been published for " + locationChoice.getSelectedLocation() + "!");
            });
            dialogConfirmPublish.open();

        }
    }

    private void buildSchedule() {
        detailsLayout.removeAll();

        CalendarOptions calendarOptions = new CalendarOptions();
        //get the last used view for the logged in user - if any
        Long driverIdForView = signedInDriver.get().getFleetId();

        SlotLabelFormat slotLabelFormat = new SlotLabelFormat();
        slotLabelFormat.setWeekday("short");
        slotLabelFormat.setMonth("short");
        slotLabelFormat.setDay("numeric");
        calendarOptions.setSlotLabelFormat(slotLabelFormat);
        calendarOptions.setInitialView(currentSchedulerView.getClientSideValue());
        calendarOptions.setSlotDuration("12:00:00");
        calendarOptions.setBusinessHours(true);
        calendarOptions.setResourceAreaWidth("225px");
        calendarOptions.setResourceGroupField("group");

        scheduler = FullCalendarBuilder.create()
                .withCustomType(FullCalendarWithTooltip.class) // create a new instance with a custom type
                .withAutoBrowserTimezone()
            .withInitialOptions(calendarOptions.getObjectNode())
                .withEntryLimit(3)
                .withScheduler("GPL-My-Project-Is-Open-Source")
                .build();

        schedulerMenuBar = new SchedulerMenuBar(scheduler,currentSchedulerView, isOnlyUser);

        scheduler.setResourceAreaHeaderContent("Drivers");
        scheduler.setWeekNumbersVisible(false);
        scheduler.setResourcesInitiallyExpanded(true);
        scheduler.setSlotMinTime(Scheduler.minTime);
        scheduler.setSlotMaxTime(Scheduler.maxTime);
        scheduler.setBusinessHours(
            BusinessHours.of(BusinessHours.ALL_DAYS)
                .start(Scheduler.minTime)
                .end(Scheduler.maxTime));
        scheduler.allowDatesRenderEventOnOptionChange(false);

        scheduler.addDayNumberClickedListener(e -> {
            //log.info("DayNumberClickedListener");
        });

        scheduler.addBrowserTimezoneObtainedListener(e -> {
            //log.info("BrowserTimezoneObtainedListener: timeZone:" + e.getTimezone().toString());
        });

        //clicked occurs AFTER SlotsSelected and is not called on dragging selection of multiple slots
        scheduler.addTimeslotClickedSchedulerListener(e -> {
            //log.info("TimeslotClickedSchedulerListener: Clicked on Driver:" + e.getResource().get().getTitle() + " at time:" + e.getDateTime());
        });

        scheduler.addTimeslotsSelectedSchedulerListener(e -> {
            log.info("TimeslotsSelectedSchedulerListener: Clicked on Driver:" + e.getResource().get().getTitle() + " at time:" + e.getStartWithOffset() + " end:" + e.getEndWithOffset());
            //log.info("ZoneId: " + e.getSource().getTimezone().getZoneId() );
            LocalDateTime startDate = e.getStart();
            //log.info("e.isAllDay():" + e.isAllDay() + " getStart(): " + e.getStart() + " getEnd():" + e.getEnd() + " getEnd() minus minute:" + e.getEnd().minusMinutes(1));
            //log.info("e.isAllDay():" + e.isAllDay() + " getStartWithOffset():  " + e.getStartWithOffset() + " getEndWithOffset():" + e.getEndWithOffset() );

            //prevent multidate selection
            if(e.isAllDay() && !e.getStart().toLocalDate().equals(e.getEnd().minusMinutes(1).toLocalDate())){
                UIUtilities.showNotificationError("Multiple days selected. Please select only a single date.");
            }else if(!e.isAllDay() && !e.getStartWithOffset().toLocalDate().equals(e.getEndWithOffset().toLocalDate())) {
                UIUtilities.showNotificationError("Multiple days selected. Please select only a single date.");
            }else if(isOnlyUser && !e.getResource().get().getId().equals(signedInDriver.get().getFleetId().toString())){
                //do nothing as cannot edit other drivers records
            }else{
                eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.NEW);
                SchedulerEvent newEvent;
                if(e.isAllDay()){
                    newEvent = new SchedulerEvent(Scheduler.EventType.OFF, LocalDateTime.of(e.getStart().toLocalDate(), Scheduler.minTime), LocalDateTime.of(e.getEnd().toLocalDate(), Scheduler.maxTime), e.getResource().get().getId(), true,false, locationChoice.getSelectedLocationId());
                }else{
                    newEvent = new SchedulerEvent(Scheduler.EventType.SHIFT, e.getStartWithOffset(), e.getEndWithOffset(), e.getResource().get().getId(), false,false, locationChoice.getSelectedLocationId());
                }
                eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.NEW);
                eventDialog.dialogOpen(newEvent, isOnlyUser, signedInDriver.get().getFleetId());
            }

        });

        scheduler.addDatesRenderedListener(e -> {
            //log.info("DatesRenderedListener: called: start:" + e.getStart() + " intervalStart:" + e.getIntervalStart() + " isFromClient:" + e.isFromClient());
            if(viewChanged){
            }else if(!currentShowAllDrivers.equals(schedulerMenuBar.getShowAllDrivers())){
                //log.info("DatesRenderedListener: show all driver changed: from:" + currentShowAllDrivers + " to:" + schedulerMenuBar.getShowAllDrivers());
                currentShowAllDrivers = schedulerMenuBar.getShowAllDrivers();
                configureScheduler();
            }
            schedulerMenuBar.updateInterval(e.getIntervalStart());
            startDate = e.getIntervalStart();
            endDate = e.getIntervalEnd();
            //schedulerService.buildSchedulerResources(scheduler, startDate, endDate);
            schedulerService.refresh(scheduler, startDate, endDate, locationChoice.getSelectedLocationId());
        });

        scheduler.addEntryDroppedSchedulerListener(e -> {
            log.info("EntryDroppedSchedulerListener: JsonObject:" + e.getJsonObject());
            //log.info("EntryDroppedSchedulerListener: Entry:" + e.getEntry());
            //log.info("EntryDroppedSchedulerListener: OldResource:" + e.getOldResource());
            //log.info("EntryDroppedSchedulerListener: NewResource:" + e.getNewResource());
            eventDialog.checkForConflict(e, currentSchedulerView);
        });

        scheduler.addEntryResizedListener(e -> {
            log.info("EntryResizedListener: JsonObject:" + e.getJsonObject());
            //log.info("EntryResizedListener: Entry:" + e.getEntry());
            eventDialog.checkForConflict(e, currentSchedulerView);
        });

        scheduler.addEntryClickedListener(e -> {
            if (e.getEntry().getDisplayMode() != DisplayMode.BACKGROUND && e.getEntry().getDisplayMode() != DisplayMode.INVERSE_BACKGROUND) {
                ResourceEntry resourceEntry = (ResourceEntry) e.getEntry();
                eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.EDIT);
                //log.info("EntryClickedListener: id:" + resourceEntry.getId() + " title:" + resourceEntry.getTitle());
                SchedulerEvent thisEvent = schedulerService.getSchedulerEventById(resourceEntry.getId());
                if(thisEvent==null){
                    UIUtilities.showNotificationError("Event not found in database:" + resourceEntry.getTitle() + " resource:" + resourceEntry.getResource().get().getTitle() + " editable:" + resourceEntry.isEditable());
                }else{
                    eventDialog.setDialogMode(SchedulerEventDialog.DialogMode.EDIT);
                    eventDialog.dialogOpen(thisEvent, isOnlyUser, signedInDriver.get().getFleetId());
                }
            }
        });

        scheduler.addViewSkeletonRenderedListener(e -> {
            //log.info("ViewSkeletonRenderedListener:" + currentSchedulerView);
            //only called when the base view type changes - a TimeLine to a different TimeLine does not trigger this
        });

        configureScheduler();
        scheduler.allowDatesRenderEventOnOptionChange(true);

    }

    private void refreshScheduler(){
        log.info("refreshScheduler: refreshing to undo changes");
        //entryProvider.refreshAll();
        scheduler.getEntryProvider().refreshAll();
    }

    private void configureScheduler(){
        log.info("configureScheduler: loading config for:" + currentSchedulerView);
        if(currentSchedulerView.equals(SchedulerView.RESOURCE_TIMELINE_WEEK) || currentSchedulerView.equals(SchedulerView.RESOURCE_TIMELINE_MONTH)){
            SlotLabelFormat slotLabelFormat = new SlotLabelFormat();
            slotLabelFormat.setWeekday("short");
            slotLabelFormat.setMonth("short");
            slotLabelFormat.setDay("numeric");
            scheduler.setOption("slotLabelFormat", slotLabelFormat.getJsonObject());
            scheduler.setOption("slotDuration", "12:00:00");
            scheduler.setOption("businessHours", "true");
            scheduler.setOption("resourceGroupField", "group");
            scheduler.setOption("resourceOrder", "title");
            scheduler.setOption("navLinks", false); //remove the clickable dates as can use the toolbar

            scheduler.setOption("resourceAreaWidth", "225px");
            scheduler.setSlotMinWidth("120");

            scheduler.setSlotMinTime(Scheduler.minTime);
            scheduler.setSlotMaxTime(Scheduler.maxTime);


            //buildSchedulerResources(false);
        }else if(currentSchedulerView.equals(CalendarViewImpl.LIST_WEEK)){
            scheduler.setOption("businessHours", "true");
            scheduler.setOption("navLinks", false); //remove the clickable dates as can use the toolbar
        }else{
            log.info("configureScheduler: no specific config for:" + currentSchedulerView);
        }

        schedulerService.setSchedulerView(currentSchedulerView);
        if(isOnlyUser){
            scheduler.setTimeslotsSelectable(true);
            scheduler.setEntryStartEditable(false);
            scheduler.setEntryResizableFromStart(false);
            scheduler.setEntryResourceEditable(false);
            scheduler.setEntryDurationEditable(false);
            scheduler.setEditable(false);
            schedulerService.setShowSingleDriverId(signedInDriver.get().getFleetId());
            schedulerService.setAllowEdit(Boolean.FALSE);
        }else{
            scheduler.setTimeslotsSelectable(true);
            //TODO: temp disabled drag drop
            scheduler.setEntryStartEditable(false);
            scheduler.setEntryResizableFromStart(false);
            scheduler.setEntryResourceEditable(false);
            scheduler.setEntryDurationEditable(false);
            scheduler.setEditable(false);
            schedulerService.setAllowEdit(Boolean.FALSE);

            //scheduler.setEntryStartEditable(true);
            //scheduler.setEntryResizableFromStart(true);
            //scheduler.setEntryResourceEditable(true);
            //scheduler.setEntryDurationEditable(true);
            //scheduler.setEditable(true);
            schedulerService.setShowSingleDriverId(null);
            //schedulerService.setAllowEdit(Boolean.TRUE);
        }
        schedulerService.setIncludeAllDrivers(schedulerMenuBar.getShowAllDrivers());
        schedulerService.buildSchedulerResources(scheduler, startDate, endDate, locationChoice.getSelectedLocationId());
        schedulerService.refresh(scheduler, startDate, endDate, locationChoice.getSelectedLocationId());

    }

    @Override
    public void schedulerRefreshNeeded() {
        //schedulerService.buildSchedulerResources(scheduler, startDate, endDate);
        schedulerService.refresh(scheduler, startDate, endDate, locationChoice.getSelectedLocationId());
    }

    @Override
    public void locationChanged() {
        //for admin save the last location
        if(!isOnlyUser){
            Config.getInstance().setSetting(driverIdForView.toString(),locationChoice.getLastLocatioinUsed(),locationChoice.getSelectedLocationId().toString());
        }
        schedulerService.refresh(scheduler, startDate, endDate, locationChoice.getSelectedLocationId());
    }
}
