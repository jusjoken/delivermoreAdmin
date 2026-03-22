package ca.admin.delivermore.views.drivers;

import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.*;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.collector.data.tookan.TaskDetail;
import ca.admin.delivermore.components.custom.ButtonNumberField;
import ca.admin.delivermore.data.entity.DriverAdjustment;
import ca.admin.delivermore.data.entity.DriverAdjustmentTemplate;
import ca.admin.delivermore.data.entity.DriverCardTip;
import ca.admin.delivermore.collector.data.entity.DriverPayoutEntity;
import ca.admin.delivermore.data.intuit.JournalEntry;
import ca.admin.delivermore.data.report.*;
import ca.admin.delivermore.data.service.DriverAdjustmentRepository;
import ca.admin.delivermore.data.service.DriverAdjustmentTemplateRepository;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.TaskDetailService;
import ca.admin.delivermore.data.service.intuit.controller.QBOResult;
import ca.admin.delivermore.data.service.intuit.domain.OAuth2Configuration;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.vaadin.componentfactory.DateRange;
import com.vaadin.componentfactory.EnhancedDateRangePicker;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.ConverterTypeVia;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.core.XDocReportException;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import org.apache.commons.collections4.comparators.FixedOrderComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.vaadin.olli.FileDownloadWrapper;

import jakarta.annotation.security.RolesAllowed;
import java.io.*;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

@PageTitle("Driver Payouts")
@Route(value = "driverpayouts", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class DriverPayoutView extends Main implements TaskListRefreshNeededListener {
    @Value("classpath:PayStatement_Template.docx")
    private Resource resourcePayStatementTemplate;

    @Value("classpath:PaySummary_Template.docx")
    private Resource resourcePaySummaryTemplate;

    private Logger log = LoggerFactory.getLogger(DriverPayoutView.class);

    DriverPayoutPeriod driverPayoutPeriod = new DriverPayoutPeriod();

    private File appPath = new File(System.getProperty("user.dir"));
    private File outputDir = new File(appPath,"tozip");

    LocalDate startDate;
    LocalDate endDate;

    @Autowired
    private EmailService emailService;

    private OAuth2Configuration oAuth2Configuration;

    private EnhancedDateRangePicker rangeDatePicker = new EnhancedDateRangePicker("Select payout period:");
    private VerticalLayout detailsLayout = new VerticalLayout();
    private TaskDetailService service;
    private DriverAdjustmentRepository driverAdjustmentRepository;
    private DriverAdjustmentTemplateRepository driverAdjustmentTemplateRepository;

    private DriversRepository driversRepository;

    private String[] columns;
    private String[] columnsUpper;

    @Override
    public void taskListRefreshNeeded() {
        //perform full refresh
        //buildDriverPayoutDetails();
        refreshDriverPayoutDetails();
    }

    public enum DialogMode{
        NEW, EDIT, NEW_FIXED_DRIVER, DELETE
    }
    //DriverAdjustmentDialog fields
    private Dialog daDialog = new Dialog();
    private DialogMode daDialogMode = DialogMode.EDIT;
    private ComboBox<Driver> daDialogDriver = new ComboBox<>("Driver");
    private Checkbox daDialogRangeCreate = new Checkbox("Add using range",false);

    private CheckboxGroup<DayOfWeek> daDialogDOWGroup = new CheckboxGroup<>();

    private DatePicker daDialodAdjustmentDate = new DatePicker("Adjustment date");
    private DatePicker daDialodAdjustmentEndDate = new DatePicker("Range end date");
    private ButtonNumberField daDialodAdjustmentAmount = UIUtilities.getButtonNumberField("Adjustment amount",false,"$");
    private ComboBox<DriverAdjustmentTemplate> daDialodAdjustmentNote = new ComboBox<>("Adjustment note");
    private Button daDialogOkButton = new Button("OK");
    private Button daDialogCancelButton = new Button("Cancel");
    private Button daDialogCloseButton = new Button(new Icon("lumo", "cross"));

    private List<DriverAdjustmentTemplate> driverAdjustmentTemplateList = new ArrayList<>();

    private DriverPayoutEntity selectedDriverPayoutEntity;
    private DriverAdjustment selectedDriverAdjustment;

    private Map<Long, Binder<DriverPayoutWeek>> weekBinderMap = new TreeMap<>();
    private Map<DriverPayoutPk, Binder<DriverPayoutDay>> dayBinderMap = new TreeMap<>();
    private Map<DriverPayoutPk, Grid> dayGridMap = new TreeMap<>();
    private Map<Long, Grid> weekGridMap = new TreeMap<>();

    private Binder<DriverPayoutPeriod> periodBinder = new Binder<>(DriverPayoutPeriod.class);

    private MissingGlobalDataDetails missingGlobalDataDetails = new MissingGlobalDataDetails();
    private TaskEditDialog taskEditDialog = new TaskEditDialog();

    public DriverPayoutView(@Autowired TaskDetailService service, @Autowired DriverAdjustmentRepository driverAdjustmentRepository, @Autowired DriversRepository driversRepository, @Autowired OAuth2Configuration oAuth2Configuration, @Autowired DriverAdjustmentTemplateRepository driverAdjustmentTemplateRepository) {
        this.service = service;
        this.driverAdjustmentRepository = driverAdjustmentRepository;
        this.driverAdjustmentTemplateRepository = driverAdjustmentTemplateRepository;
        this.driversRepository = driversRepository;
        this.oAuth2Configuration = oAuth2Configuration;

        driverAdjustmentTemplateList = driverAdjustmentTemplateRepository.findAll();

        configureDatePicker();
        startDate = rangeDatePicker.getValue().getStartDate();
        endDate = rangeDatePicker.getValue().getEndDate();

        missingGlobalDataDetails.getTaskEditDialog().addListener(this);
        taskEditDialog.addListener(this);

        configureColumns();
        daDialogConfigure();

        buildDriverPayoutDetails();

        add(getToolbar(), getContent());

    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(rangeDatePicker);
        toolbar.setPadding(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        return toolbar;
    }

    private void configureDatePicker() {
        LocalDate defaultDate = LocalDate.parse("2022-08-14");

        //get lastWeek as the default for the range picker
        LocalDate nowDate = LocalDate.now();
        LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate startOfLastWeek = prevSun.minusWeeks(1);
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);

        rangeDatePicker.setMin(defaultDate);
        rangeDatePicker.setValue(new DateRange(startOfLastWeek,endOfLastWeek));
        rangeDatePicker.addValueChangeListener(e -> {
            startDate = rangeDatePicker.getValue().getStartDate();
            endDate = rangeDatePicker.getValue().getEndDate();
            buildDriverPayoutDetails();

        });
    }

    private void configureColumns() {
        columns = new String[]{
                "fleetName",
                "creationDate",
                "creationDateTime",
                "restaurantName",
                "customerUsername",
                "driverIncome",
                "driverCash",
                "driverPayout",
                "paymentMethod",
                "totalSale",
                "driverPay",
                "tip",
                "tipInNotesIssue",
                "notes",
                "fleetId",
                "webOrder",
                "jobId"
        };

        columnsUpper = columns.clone();
        for(int i=0;i<columnsUpper.length;i++){
            columnsUpper[i] = columnsUpper[i].toUpperCase();
        }

    }

    private Component getContent() {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.add(detailsLayout);
        detailsLayout.setSizeFull();
        return mainLayout;
    }

    private void buildDriverPayoutDetails(){
        detailsLayout.removeAll();
        weekGridMap.clear();
        weekBinderMap.clear();
        dayBinderMap.clear();

        List<PayoutDocument> selectedPayoutDocuments = new ArrayList<>();

        TaskDetailRepository taskDetailRepository = Registry.getBean(TaskDetailRepository.class);

        if(endDate==null){
            endDate = startDate;
        }
        log.info("buildDriverPayoutDetails: start:" + startDate + " end:" + endDate);

        String location = "Strathmore";
        driverPayoutPeriod = new DriverPayoutPeriod(location, startDate, endDate);

        //Summary
        VerticalLayout periodSummaryVerticalLayout = new VerticalLayout();
        HorizontalLayout periodSummaryLayout = UIUtilities.getHorizontalLayout();
        String summaryTitle = "Period Summary: " + startDate + " - " + endDate;
        periodSummaryVerticalLayout.add(new Text(summaryTitle),periodSummaryLayout);
        NumberField driverPayoutPeriodTip = UIUtilities.getNumberField("Tip");
        periodBinder.bind(driverPayoutPeriodTip, DriverPayoutPeriod::getTip, DriverPayoutPeriod::setTip);
        NumberField driverPayoutPeriodIncome = UIUtilities.getNumberField("Income");
        periodBinder.bind(driverPayoutPeriodIncome, DriverPayoutPeriod::getDriverIncome, DriverPayoutPeriod::setDriverIncome);
        NumberField driverPayoutPeriodCash = UIUtilities.getNumberField("Cash");
        periodBinder.bind(driverPayoutPeriodCash, DriverPayoutPeriod::getDriverCash, DriverPayoutPeriod::setDriverCash);
        NumberField driverPayoutPeriodAdjustment = UIUtilities.getNumberField("Adjustment");
        periodBinder.bind(driverPayoutPeriodAdjustment, DriverPayoutPeriod::getDriverAdjustment, DriverPayoutPeriod::setDriverAdjustment);
        NumberField driverPayoutPeriodPayout = UIUtilities.getNumberField("Payout");
        periodBinder.bind(driverPayoutPeriodPayout, DriverPayoutPeriod::getDriverPayout, DriverPayoutPeriod::setDriverPayout);
        periodSummaryLayout.add(
                UIUtilities.getTextFieldRO("Deliveries", driverPayoutPeriod.getTaskCount().toString(), "100px"),
                UIUtilities.getNumberField("Pay",driverPayoutPeriod.getDriverPay()),
                driverPayoutPeriodTip,
                driverPayoutPeriodIncome,
                driverPayoutPeriodCash,
                driverPayoutPeriodAdjustment,
                driverPayoutPeriodPayout
        );
        periodSummaryLayout.setAlignItems(FlexComponent.Alignment.END);
        Details summaryDetails = new Details(periodSummaryVerticalLayout);
        summaryDetails.addThemeVariants(DetailsVariant.FILLED);
        //summaryDetails.setWidthFull();
        summaryDetails.setSizeUndefined();
        detailsLayout.add(summaryDetails);
        periodBinder.readBean(driverPayoutPeriod);
        VerticalLayout summaryDetailsContent = new VerticalLayout();
        summaryDetails.add(summaryDetailsContent);

        //Add the Documents review Details pane
        Details periodDocuments = new Details("Documents");
        periodDocuments.addThemeVariants(DetailsVariant.FILLED);
        periodDocuments.setWidthFull();
        summaryDetailsContent.add(periodDocuments);
        Grid<PayoutDocument> periodDocumentsGrid = new Grid<>();
        weekGridMap.put(3L,periodDocumentsGrid);

        HorizontalLayout periodDocumentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);

        Button postJournalEntries = new Button("Post to QBO");
        postJournalEntries.setDisableOnClick(true);
        postJournalEntries.setEnabled(false);
        postJournalEntries.addClickListener(e -> {
            if(driverPayoutPeriod.getJournalEntries().size()==0){
                Notification.show("Failed: no journal entries created. You may need to connect to QBO");
            }else{
                for (JournalEntry journalEntry:driverPayoutPeriod.getJournalEntries()) {
                    QBOResult qboResult = journalEntry.post();
                    log.info("buildDriverPayoutDetails: qboResult:" + qboResult.getMessage());
                    if(qboResult.getSuccess()){
                        Notification.show("Journal Entry:" + journalEntry.getDocNumber() + " posted");
                    }else{
                        Notification.show("Failed to post Journal Entry:" + journalEntry.getDocNumber());
                    }
                }
            }
        });

        Button createDocuments = new Button("Create documents");
        createDocuments.setDisableOnClick(true);
        createDocuments.addClickListener(e -> {
            createStatements();
            periodDocumentsGrid.setItems(driverPayoutPeriod.getPayoutDocuments());
            periodDocumentsGrid.getDataProvider().refreshAll();
            createDocuments.setEnabled(true);
            postJournalEntries.setEnabled(true);
        });
        Button emailDocumentsButton = new Button("Send documents");
        emailDocumentsButton.setDisableOnClick(true);
        emailDocumentsButton.setEnabled(false);
        emailDocumentsButton.addClickListener(e -> {
            for (PayoutDocument payoutDocument:selectedPayoutDocuments) {
                if(payoutDocument.getEmailAddress()==null || payoutDocument.getEmailAddress().isEmpty()){
                    //log.info("Skipping selected item:" + payoutDocument.getName() + " as no email");
                }else{
                    Notification.show("Sending:" + payoutDocument.getName());
                    String subject = "DeliverMore " + payoutDocument.getName() + " Period:" + driverPayoutPeriod.getPayoutPeriodStart() + " - " + driverPayoutPeriod.getPayoutPeriodEnd();
                    String body = "";
                    emailService.sendMailWithAttachment(payoutDocument.getEmailAddress(), subject, body, payoutDocument.getFile(), payoutDocument.getFile().getName());
                    //log.info("Selected item to email:" + payoutDocument.getName() + " send to:" + payoutDocument.getEmailAddress());
                }
            }
            emailDocumentsButton.setEnabled(true);
        });
        Button zipDocumentsButton = new Button("Zip documents");
        zipDocumentsButton.setDisableOnClick(true);
        zipDocumentsButton.setEnabled(false);

        FileDownloadWrapper zipDocumentsButtonWrapper = new FileDownloadWrapper(
                new StreamResource("DriverPayoutFiles.zip", () -> {
                    try {
                        File zippedFile = getZippedFileforFileList(selectedPayoutDocuments);
                        zipDocumentsButton.setEnabled(true);
                        return new ByteArrayInputStream(Files.readAllBytes( zippedFile.toPath()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
        );
        zipDocumentsButtonWrapper.wrapComponent(zipDocumentsButton);

        periodDocumentsToolbar.add(createDocuments,emailDocumentsButton,zipDocumentsButtonWrapper);

        if(oAuth2Configuration.isConfigured()){
            periodDocumentsToolbar.add(postJournalEntries);
        }

        VerticalLayout periodDocumentsContent = UIUtilities.getVerticalLayout();
        periodDocumentsContent.add(periodDocumentsToolbar);
        periodDocuments.add(periodDocumentsContent);
        //add checkbox group of all documents
        periodDocumentsContent.add(periodDocumentsGrid);
        periodDocumentsGrid.setWidthFull();
        periodDocumentsGrid.setAllRowsVisible(true);
        periodDocumentsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        periodDocumentsGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        periodDocumentsGrid.setItems(driverPayoutPeriod.getPayoutDocuments());
        periodDocumentsGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        periodDocumentsGrid.addComponentColumn(item -> {
            Anchor anchor = new Anchor(item.getResource(), item.getName());
            anchor.setTarget("_blank");
            return anchor;
        }).setHeader("Document Link");
        periodDocumentsGrid.addColumn(PayoutDocument::getEmailPresentation).setHeader("Email");
        periodDocumentsGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        periodDocumentsGrid.addSelectionListener(selection -> {
            selectedPayoutDocuments.clear();
            selectedPayoutDocuments.addAll(selection.getAllSelectedItems());
            emailDocumentsButton.setEnabled(false);
            zipDocumentsButton.setEnabled(false);
            for (PayoutDocument payoutDocument: selection.getAllSelectedItems()) {
                if(!payoutDocument.getEmailAddress().isEmpty()){
                    emailDocumentsButton.setEnabled(true);
                    zipDocumentsButton.setEnabled(true);
                    break;
                }
            }
        });

        //add the Missing Global Data list
        summaryDetailsContent.add(missingGlobalDataDetails.buildMissingGlobalData(startDate,endDate));

        //Add the adjustments in a Grid to the summary content within a Details
        HorizontalLayout periodAdustmentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);
        Icon addNewIcon = new Icon("lumo", "plus");
        addNewIcon.setColor("green");
        Button adjustmentsAddNew = new Button("Add", addNewIcon);
        adjustmentsAddNew.addThemeVariants(ButtonVariant.LUMO_SMALL);
        adjustmentsAddNew.addClickListener(e -> {
            daDialogMode = DialogMode.NEW;
            daDialogOpen(new DriverAdjustment(), daDialogMode);
        });
        periodAdustmentsToolbar.add(adjustmentsAddNew);
        Details periodAdjustments = new Details("Adjustments");
        periodAdjustments.addThemeVariants(DetailsVariant.FILLED);
        periodAdjustments.setWidthFull();
        summaryDetailsContent.add(periodAdjustments);
        periodAdjustments.setOpened(true);

        Grid<DriverAdjustment> summaryAdjustmentGrid = new Grid<>();
        weekGridMap.put(0L,summaryAdjustmentGrid);
        summaryAdjustmentGrid.setItems(driverPayoutPeriod.getDriverAdjustmentList());
        summaryAdjustmentGrid.setWidthFull();
        summaryAdjustmentGrid.setAllRowsVisible(true);
        summaryAdjustmentGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        summaryAdjustmentGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        summaryAdjustmentGrid.addComponentColumn(item -> {
            Icon editIcon = new Icon("lumo", "edit");
            //Button editButton = new Button("Edit");
            editIcon.addClickListener(e -> {
                daDialogMode = DialogMode.EDIT;
                daDialogOpen(item, daDialogMode);
            });
            return editIcon;
        }).setWidth("150px").setFlexGrow(0);
        summaryAdjustmentGrid.addComponentColumn(item -> {
            Icon deleteIcon = new Icon("lumo", "cross");
            deleteIcon.setColor("red");
            deleteIcon.addClickListener(e -> {
                daDialogMode = DialogMode.DELETE;
                daDialogOpen(item, daDialogMode);
            });
            return deleteIcon;
        }).setWidth("150px").setFlexGrow(0);
        summaryAdjustmentGrid.addColumn(DriverAdjustment::getFleetName).setHeader("Driver");
        summaryAdjustmentGrid.addColumn(DriverAdjustment::getAdjustmentDate).setHeader("Date");
        summaryAdjustmentGrid.addColumn(DriverAdjustment::getAdjustmentNote).setHeader("Note");
        summaryAdjustmentGrid.addColumn(item -> item.getAdjustmentAmountFmt())
                .setHeader("Amount")
                .setTextAlign(ColumnTextAlign.END);
        summaryAdjustmentGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        VerticalLayout periodAdjustmentsContent = UIUtilities.getVerticalLayout();
        periodAdjustmentsContent.add(periodAdustmentsToolbar, summaryAdjustmentGrid);
        periodAdjustments.add(periodAdjustmentsContent);

        //Add the driver card tip list in a Grid to the summary content within a Details
        Details periodCardTips = new Details("Tips via Card (Total:" + driverPayoutPeriod.getCardTip() + ")");
        periodCardTips.addThemeVariants(DetailsVariant.FILLED);
        periodCardTips.setWidthFull();
        summaryDetailsContent.add(periodCardTips);
        periodCardTips.setOpened(true);

        Grid<DriverCardTip> summaryCardTipGrid = new Grid<>();
        weekGridMap.put(1L,summaryCardTipGrid);
        summaryCardTipGrid.setItems(driverPayoutPeriod.getDriverCardTipList());
        summaryCardTipGrid.setWidthFull();
        summaryCardTipGrid.setAllRowsVisible(true);
        summaryCardTipGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        summaryCardTipGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        summaryCardTipGrid.addColumn(DriverCardTip::getFleetName).setHeader("Driver");
        summaryCardTipGrid.addColumn(item -> item.getCardTipAmountFmt())
                .setHeader("Amount")
                .setTextAlign(ColumnTextAlign.END);
        summaryCardTipGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        periodCardTips.add(summaryCardTipGrid);

        //Add the driver tip issues list in a Grid to the summary content within a Details
        Details periodTipIssues = new Details("Potential tip issues");
        periodTipIssues.addThemeVariants(DetailsVariant.FILLED);
        periodTipIssues.setWidthFull();
        summaryDetailsContent.add(periodTipIssues);
        periodTipIssues.setOpened(true);

        Grid<DriverPayoutEntity> summaryTipIssuesGrid = new Grid<>();
        weekGridMap.put(2L,summaryTipIssuesGrid);
        summaryTipIssuesGrid.setItems(driverPayoutPeriod.getTipIssues());
        summaryTipIssuesGrid.setWidthFull();
        summaryTipIssuesGrid.setAllRowsVisible(true);
        summaryTipIssuesGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        summaryTipIssuesGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        summaryTipIssuesGrid.addComponentColumn(item -> {
            Icon editIcon = new Icon("lumo", "edit");
            editIcon.addClickListener(e -> {
                taskEditDialog.setDialogMode(TaskEditDialog.DialogMode.EDIT);
                taskEditDialog.dialogOpen(item.getJobId());
            });
            return editIcon;
        }).setWidth("150px").setFlexGrow(0);
        summaryTipIssuesGrid.addColumn(DriverPayoutEntity::getFleetName).setHeader("Driver");
        summaryTipIssuesGrid.addColumn(new LocalDateTimeRenderer<>(DriverPayoutEntity::getCreationDateTime,"MM-dd HH:mm"))
                .setHeader("Date");
        summaryTipIssuesGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getTip())).setHeader("Tip").setTextAlign(ColumnTextAlign.END);
        summaryTipIssuesGrid.addColumn(DriverPayoutEntity::getNotes).setHeader("Notes");
        summaryTipIssuesGrid.addColumn(DriverPayoutEntity::getJobId).setHeader("Task Id");
        summaryTipIssuesGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        periodTipIssues.add(summaryTipIssuesGrid);

        //Drivers - week
        for (DriverPayoutWeek driverPayoutWeek: driverPayoutPeriod.getDriverPayoutWeekList()) {
            //detailsLayout.add(driverPayoutWeek.getDetails(weekBinderMap,dayBinderMap,dayGridMap,weekGridMap,taskDetailRepository,driversRepository,taskEditDialog,daDialogMode));

            //TODO: move all this to DriverPayoutWeek so it can be called from here AND from MyPay
            HorizontalLayout driverSummaryLayout = UIUtilities.getHorizontalLayout();

            Binder<DriverPayoutWeek> weekBinder = new Binder<>(DriverPayoutWeek.class);
            //log.info("DriverPayoutWeek: getFleetId:" + driverPayoutWeek.getFleetId());
            weekBinderMap.put(driverPayoutWeek.getFleetId(),weekBinder);
            NumberField driverPayoutWeekTip = UIUtilities.getNumberField("Tip");
            weekBinder.bind(driverPayoutWeekTip, DriverPayoutWeek::getTip, DriverPayoutWeek::setTip);
            NumberField driverPayoutWeekIncome = UIUtilities.getNumberField("Income");
            weekBinder.bind(driverPayoutWeekIncome, DriverPayoutWeek::getDriverIncome, DriverPayoutWeek::setDriverIncome);
            NumberField driverPayoutWeekCash = UIUtilities.getNumberField("Cash");
            weekBinder.bind(driverPayoutWeekCash, DriverPayoutWeek::getDriverCash, DriverPayoutWeek::setDriverCash);
            NumberField driverPayoutWeekAdjustment = UIUtilities.getNumberField("Adjustment");
            weekBinder.bind(driverPayoutWeekAdjustment, DriverPayoutWeek::getDriverAdjustment, DriverPayoutWeek::setDriverAdjustment);
            NumberField driverPayoutWeekPayout = UIUtilities.getNumberField("Payout");
            weekBinder.bind(driverPayoutWeekPayout, DriverPayoutWeek::getDriverPayout, DriverPayoutWeek::setDriverPayout);
            driverSummaryLayout.add(
                    UIUtilities.getTextFieldRO("Driver", driverPayoutWeek.getFleetName().toString()),
                    UIUtilities.getTextFieldRO("Deliveries", driverPayoutWeek.getTaskCount().toString(), "100px"),
                    UIUtilities.getNumberField("Pay",driverPayoutWeek.getDriverPay()),
                    driverPayoutWeekTip,
                    driverPayoutWeekIncome,
                    driverPayoutWeekCash,
                    driverPayoutWeekAdjustment,
                    driverPayoutWeekPayout
            );

            Details driverDetails = new Details(driverSummaryLayout);
            driverDetails.addThemeVariants(DetailsVariant.FILLED);
            //driverDetails.setWidthFull();
            driverDetails.setSizeUndefined();
            detailsLayout.add(driverDetails);
            weekBinder.readBean(driverPayoutWeek);

            //Drivers - day
            VerticalLayout driverDetailsContent = new VerticalLayout();
            for (DriverPayoutDay driverPayoutDay: driverPayoutWeek.getDriverPayoutDayList()) {
                Binder<DriverPayoutDay> dayBinder = new Binder<>(DriverPayoutDay.class);
                dayBinderMap.put(driverPayoutDay.getDriverPayoutPk(),dayBinder);
                HorizontalLayout driverDaySummaryLayout = UIUtilities.getHorizontalLayout();

                NumberField driverPayoutDayTip = UIUtilities.getNumberField("Tip");
                dayBinder.bind(driverPayoutDayTip, DriverPayoutDay::getTip, DriverPayoutDay::setTip);
                NumberField driverPayoutDayIncome = UIUtilities.getNumberField("Income");
                dayBinder.bind(driverPayoutDayIncome, DriverPayoutDay::getDriverIncome, DriverPayoutDay::setDriverIncome);
                NumberField driverPayoutDayCash = UIUtilities.getNumberField("Cash");
                dayBinder.bind(driverPayoutDayCash, DriverPayoutDay::getDriverCash, DriverPayoutDay::setDriverCash);
                NumberField driverPayoutDayPayout = UIUtilities.getNumberField("Payout");
                dayBinder.bind(driverPayoutDayPayout, DriverPayoutDay::getDriverPayout, DriverPayoutDay::setDriverPayout);
                driverDaySummaryLayout.add(
                        UIUtilities.getTextFieldRO("Date", driverPayoutDay.getPayoutDate().toString()),
                        UIUtilities.getTextFieldRO("Deliveries", driverPayoutDay.getTaskCount().toString(), "100px"),
                        UIUtilities.getNumberField("Pay",driverPayoutDay.getDriverPay()),
                        driverPayoutDayTip,
                        driverPayoutDayIncome,
                        driverPayoutDayCash,
                        driverPayoutDayPayout
                );
                Details driverDayDetails = new Details(driverDaySummaryLayout);
                driverDayDetails.addThemeVariants(DetailsVariant.FILLED);
                driverDayDetails.setWidthFull();
                driverDetailsContent.add(driverDayDetails);
                dayBinder.readBean(driverPayoutDay);

                //add a grid to the content of the Day
                Grid<DriverPayoutEntity> driverDayGrid = new Grid<>();
                dayGridMap.put(driverPayoutDay.getDriverPayoutPk(),driverDayGrid);
                driverDayGrid.setItems(taskDetailRepository.getDriverPayoutByFleetId(driverPayoutDay.getFleetId(), driverPayoutDay.getPayoutDate().atStartOfDay(),driverPayoutDay.getPayoutDate().atTime(23,59,59) ));
                driverDayGrid.setWidthFull();
                driverDayGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
                driverDayGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
                driverDayGrid.addComponentColumn(item -> {
                    Icon editIcon = new Icon("lumo", "edit");
                    editIcon.addClickListener(e -> {
                        taskEditDialog.setDialogMode(TaskEditDialog.DialogMode.EDIT);
                        taskEditDialog.dialogOpen(item.getJobId());
                    });
                    return editIcon;
                }).setWidth("150px").setFlexGrow(0).setFrozen(true);
                driverDayGrid.addComponentColumn(item -> {
                    Icon refreshIcon = new Icon("lumo", "reload");
                    refreshIcon.addClickListener(e -> {
                        refreshTaskFromTookan(item);
                    });
                    return refreshIcon;
                }).setWidth("150px").setFlexGrow(0).setFrozen(true);
                driverDayGrid.addColumn(new LocalDateTimeRenderer<>(DriverPayoutEntity::getCreationDateTime,"MM-dd HH:mm"))
                        .setHeader("Date");
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDriverPay())).setHeader("Pay").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getTip())).setHeader("Tip").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDriverIncome())).setHeader("Income").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDriverCash())).setHeader("Cash").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDriverPayout())).setHeader("Payout").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(DriverPayoutEntity::getRestaurantName).setHeader("Restaurant");
                driverDayGrid.addColumn(DriverPayoutEntity::getCustomerUsername).setHeader("Customer");
                driverDayGrid.addColumn(DriverPayoutEntity::getPaymentMethod).setHeader("Method");
                driverDayGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getTotalSale())).setHeader("Total Sale").setTextAlign(ColumnTextAlign.END);
                driverDayGrid.addColumn(DriverPayoutEntity::getTipInNotesIssue).setHeader("Note Issue");
                driverDayGrid.addColumn(DriverPayoutEntity::getNotes).setHeader("Notes");
                driverDayGrid.addColumn(DriverPayoutEntity::getJobId).setHeader("Task Id");
                driverDayGrid.getColumns().forEach(col -> col.setAutoWidth(true));

                driverDayDetails.add(driverDayGrid);
                //driverDayGrid.asSingleSelect().addValueChangeListener(e -> editDriverPayoutEntity(e.getValue()));

            }
            //add driver adjustments if any
            HorizontalLayout driverAdustmentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);
            Button driverAdjustmentsAddNew = new Button("Add", addNewIcon);
            driverAdjustmentsAddNew.addThemeVariants(ButtonVariant.LUMO_SMALL);
            driverAdjustmentsAddNew.addClickListener(e -> {
                daDialogMode = DialogMode.NEW_FIXED_DRIVER;
                DriverAdjustment newDriverAdjustment = new DriverAdjustment();
                newDriverAdjustment.setDriver(driversRepository.findDriverByFleetId(driverPayoutWeek.getFleetId()));
                daDialogOpen(newDriverAdjustment, daDialogMode);
            });
            driverAdustmentsToolbar.add(driverAdjustmentsAddNew);

            Details driverWeekAdjustments = new Details("Adjustments");
            driverWeekAdjustments.setOpened(true);
            driverWeekAdjustments.addThemeVariants(DetailsVariant.FILLED);
            driverWeekAdjustments.setWidthFull();
            driverDetailsContent.add(driverWeekAdjustments);
            Grid<DriverAdjustment> driverAdjustmentGrid = new Grid<>();
            driverAdjustmentGrid.setWidthFull();
            driverAdjustmentGrid.setAllRowsVisible(true);
            driverAdjustmentGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
            driverAdjustmentGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
            weekGridMap.put(driverPayoutWeek.getFleetId(),driverAdjustmentGrid);
            VerticalLayout driverAdjustmentsContent = UIUtilities.getVerticalLayout();
            driverAdjustmentsContent.add(driverAdustmentsToolbar, driverAdjustmentGrid);
            driverWeekAdjustments.add(driverAdjustmentsContent);
            driverAdjustmentGrid.addComponentColumn(item -> {
                Icon editIcon = new Icon("lumo", "edit");
                //Button editButton = new Button("Edit");
                editIcon.addClickListener(e -> {
                    daDialogMode = DialogMode.EDIT;
                    daDialogOpen(item, daDialogMode);
                });
                return editIcon;
            }).setWidth("150px").setFlexGrow(0);
            driverAdjustmentGrid.addComponentColumn(item -> {
                Icon deleteIcon = new Icon("lumo", "cross");
                deleteIcon.setColor("red");
                deleteIcon.addClickListener(e -> {
                    daDialogMode = DialogMode.DELETE;
                    daDialogOpen(item, daDialogMode);
                });
                return deleteIcon;
            }).setWidth("150px").setFlexGrow(0);
            driverAdjustmentGrid.addColumn(DriverAdjustment::getAdjustmentDate).setHeader("Date");
            driverAdjustmentGrid.addColumn(DriverAdjustment::getAdjustmentNote).setHeader("Note");
            driverAdjustmentGrid.addColumn(item -> item.getAdjustmentAmountFmt()).setHeader("Amount").setTextAlign(ColumnTextAlign.END);
            driverAdjustmentGrid.getColumns().forEach(col -> col.setAutoWidth(true));

            if(driverPayoutWeek.getDriverAdjustmentList().size()>0){
                //Add the adjustments in a Grid to the summary content
                driverAdjustmentGrid.setItems(driverPayoutWeek.getDriverAdjustmentList());
            }

            driverDetails.add(driverDetailsContent);

        }

    }

    private void saveCSV(File csvFile){

        try {
            var mappingStrategy = new HeaderColumnNameMappingStrategy<DriverPayoutEntity>();
            mappingStrategy.setType(DriverPayoutEntity.class);
            mappingStrategy.setColumnOrderOnWrite(new FixedOrderComparator(columnsUpper));

            Stream<DriverPayoutEntity> driverPayoutEntityStream = service.findAllDriverPayouts(startDate.atStartOfDay(),endDate.atTime(23,59,59)).stream();
            StringWriter output = new StringWriter();
            StatefulBeanToCsv<DriverPayoutEntity> beanToCsv = new StatefulBeanToCsvBuilder<DriverPayoutEntity>(output)
                    .withMappingStrategy(mappingStrategy)
                    .build();
            beanToCsv.write(driverPayoutEntityStream);
            var content = output.toString();
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile));
            writer.write(content);

            writer.close();
        } catch (CsvDataTypeMismatchException | CsvRequiredFieldEmptyException ex) {
            log.info("saveCSV: CSV write failed");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void createStatements() {
        //create a folder to hold all the temp files.  Empty the folder of previous runs
        String outputFileExt = ".pdf";
        Utility.emptyDir(outputDir);
        try {
            Files.createDirectory(outputDir.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(oAuth2Configuration.isConfigured()){
            driverPayoutPeriod.createJournalEntries();
        }

        //save the csv source
        String csvFileName = "PayoutDetails-" + driverPayoutPeriod.getLocation() + driverPayoutPeriod.getPayoutPeriodStart() + "-" + driverPayoutPeriod.getPayoutPeriodEnd() + ".csv";
        File csvFile = new File(outputDir,csvFileName);
        saveCSV(csvFile);
        driverPayoutPeriod.setCsvFile(csvFile);

        for (DriverPayoutWeek driverPayoutWeek: driverPayoutPeriod.getDriverPayoutWeekList()) {
            log.info("createPDFStatements: week:" + driverPayoutWeek.toString());

            String outputFileName = "PayStatement-" + driverPayoutWeek.getFleetName() + driverPayoutWeek.getPayoutDate() + "-" + driverPayoutWeek.getWeekEndDate();
            File outputFile = new File(outputDir,outputFileName + outputFileExt);
            driverPayoutWeek.setPdfFile(outputFile);

            try {
                // 1) Load Docx file by filling Velocity template engine and cache it to the registry
                InputStream in = resourcePayStatementTemplate.getInputStream();
                IXDocReport report = XDocReportRegistry.getRegistry().loadReport(in, TemplateEngineKind.Velocity);

                // 2) Create context Java model
                IContext context = report.createContext();
                //Project project = new Project("XDocReport");
                context.put("driverPayoutWeek", driverPayoutWeek);

                List<DriverPayoutDay> driverDays = driverPayoutWeek.getDriverPayoutDayList();
                // 2) Create fields metadata to manage lazy loop (#forech velocity)
                // for table row.
                FieldsMetadata metadata = new FieldsMetadata();
                metadata.addFieldAsList("driverDays.getPayoutDate()");
                metadata.addFieldAsList("driverDays.getTaskCount()");
                metadata.addFieldAsList("driverDays.getDriverPayFmt()");
                metadata.addFieldAsList("driverDays.getTipFmt()");
                metadata.addFieldAsList("driverDays.getDriverIncomeFmt()");
                metadata.addFieldAsList("driverDays.getDriverCashFmt()");
                metadata.addFieldAsList("driverDays.getDriverPayoutFmt()");
                report.setFieldsMetadata(metadata);

                context.put("driverDays", driverDays);

                List<DriverAdjustment> driverAdjustments = driverPayoutWeek.getDriverAdjustmentList();
                // 2) Create fields metadata to manage lazy loop (#forech velocity)
                // for table row.
                metadata.addFieldAsList("driverAdjustments.getAdjustmentDate()");
                metadata.addFieldAsList("driverAdjustments.getAdjustmentNote()");
                metadata.addFieldAsList("driverAdjustments.getAdjustmentAmountFmt()");

                context.put("driverAdjustments", driverAdjustments);

                // 3) Generate report by merging Java model with the Docx
                //To PDF
                OutputStream out = new FileOutputStream(outputFile);
                Options options = Options.getTo(ConverterTypeTo.PDF).via(ConverterTypeVia.XWPF);
                report.convert(context, options, out);
            } catch (IOException e) {
                log.info("createPDFStatements: FAILED week:" + driverPayoutWeek.toString() + " ERROR:" + e.toString());
                e.printStackTrace();
            } catch (XDocReportException e) {
                log.info("createPDFStatements: FAILED2 week:" + driverPayoutWeek.toString() + " ERROR:" + e.toString());
                e.printStackTrace();
            }

        }

        //Create payout summary
        String outputFileName = "PaySummary-" + driverPayoutPeriod.getLocation() + driverPayoutPeriod.getPayoutPeriodStart() + "-" + driverPayoutPeriod.getPayoutPeriodEnd();
        File outputFile = new File(outputDir,outputFileName + outputFileExt);
        driverPayoutPeriod.setPdfFile(outputFile);

        try {
            // 1) Load Docx file by filling Velocity template engine and cache it to the registry
            InputStream in = resourcePaySummaryTemplate.getInputStream();
            IXDocReport report = XDocReportRegistry.getRegistry().loadReport(in, TemplateEngineKind.Velocity);

            // 2) Create context Java model
            IContext context = report.createContext();
            //Project project = new Project("XDocReport");
            context.put("driverPayoutPeriod", driverPayoutPeriod);

            List<DriverPayoutWeek> driverWeeks = driverPayoutPeriod.getDriverPayoutWeekList();
            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            FieldsMetadata metadata = new FieldsMetadata();
            metadata.addFieldAsList("driverWeeks.getFleetName()");
            metadata.addFieldAsList("driverWeeks.getTaskCount()");
            metadata.addFieldAsList("driverWeeks.getDriverPayFmt()");
            metadata.addFieldAsList("driverWeeks.getTipFmt()");
            metadata.addFieldAsList("driverWeeks.getDriverIncomeFmt()");
            metadata.addFieldAsList("driverWeeks.getDriverCashFmt()");
            metadata.addFieldAsList("driverWeeks.getDriverAdjustmentFmt()");
            metadata.addFieldAsList("driverWeeks.getDriverPayoutFmt()");
            report.setFieldsMetadata(metadata);

            context.put("driverWeeks", driverWeeks);

            List<DriverAdjustment> driverAdjustments = driverPayoutPeriod.getDriverAdjustmentList();
            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            metadata.addFieldAsList("driverAdjustments.getFleetName()");
            metadata.addFieldAsList("driverAdjustments.getAdjustmentDate()");
            metadata.addFieldAsList("driverAdjustments.getAdjustmentNote()");
            metadata.addFieldAsList("driverAdjustments.getAdjustmentAmountFmt()");

            context.put("driverAdjustments", driverAdjustments);


            // 3) Generate report by merging Java model with the Docx
            //To PDF
            OutputStream out = new FileOutputStream(outputFile);
            Options options = Options.getTo(ConverterTypeTo.PDF).via(ConverterTypeVia.XWPF);
            report.convert(context, options, out);

        } catch (IOException e) {
            log.info("createPDFSummary: IOException period:" + driverPayoutPeriod.toString() + " ERROR:" + e.toString());
            e.printStackTrace();
        } catch (XDocReportException e) {
            log.info("createPDFSummary: XDocReportException period:" + driverPayoutPeriod.toString() + " ERROR:" + e.toString());
            e.printStackTrace();
        }


    }

    private File getZippedFileforFileList(List<PayoutDocument> docList){
        log.info("***Start of ZIP process***");
        String zipFileName = "Week " + startDate;
        File zippedFile = new File(appPath, zipFileName + ".zip");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(zipFileName + ".zip");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        for (PayoutDocument doc: docList) {
            try {
                Utility.zipFile(doc.getFile(), doc.getFile().getName(), zipOut);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            zipOut.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("***END of ZIP process***");

        return zippedFile;

    }


    //Driver Adjustment Dialog - for adding and editing driver adjustments
    public void daDialogConfigure() {
        daDialog.getElement().setAttribute("aria-label", "Edit adjustment");

        VerticalLayout dialogLayout = daDialogLayout();
        daDialog.add(dialogLayout);
        daDialog.setHeaderTitle("Driver adjustment");

        daDialogCloseButton.addClickListener((e) -> daDialog.close());
        daDialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        daDialog.getHeader().add(daDialogCloseButton);
        daDialog.setCloseOnEsc(true);
        daDialogCancelButton.addClickListener((e) -> daDialog.close());

        daDialogOkButton.addClickListener(
                event -> {
                    daDialogOkButton.setText("Wait...");
                    daDialogSave();
                }
        );
        daDialogOkButton.addClickShortcut(Key.ENTER);
        daDialogOkButton.setEnabled(false);
        daDialogOkButton.setDisableOnClick(true);

        HorizontalLayout footerLayout = new HorizontalLayout(daDialogOkButton,daDialogCancelButton);

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayout, () -> {}, Key.ENTER)
                .listenOn(footerLayout);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

        daDialog.getFooter().add(footerLayout);
    }

    private VerticalLayout daDialogLayout() {
        daDialogDriver.setItems(driversRepository.findAll(Sort.by(Sort.Direction.ASC, "name")));
        daDialogDriver.setItemLabelGenerator(Driver::getName);
        daDialogDriver.setReadOnly(true);
        daDialogDriver.setPlaceholder("Select driver");
        daDialogDriver.addValueChangeListener(item -> {
            daDialogValidate();
        });

        //set initial state to single creation
        daDialogCreateMultiMode(Boolean.FALSE);
        daDialogRangeCreate.addValueChangeListener(e -> {
            daDialogCreateMultiMode(e.getValue());
            daDialogValidate();
        });



        //DOW checkbox group
        daDialogDOWGroup.setItems(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        daDialogDOWGroup.setLabel("Select days to create adjustments for:");
        daDialogDOWGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        daDialogDOWGroup.addValueChangeListener(e -> {
            daDialogValidate();
        });

        daDialodAdjustmentNote.setItems(driverAdjustmentTemplateList);
        daDialodAdjustmentNote.setItemLabelGenerator(DriverAdjustmentTemplate::getTemplateName);
        daDialodAdjustmentNote.setRenderer(new ComponentRenderer<>(item -> new Span(item.toString())));
        daDialodAdjustmentNote.setAllowCustomValue(true);
        daDialodAdjustmentNote.setPlaceholder("Select or enter note");

        daDialodAdjustmentAmount.setButtonIcon(new Icon("vaadin", "plus-minus"));
        /*
        daDialodAdjustmentAmount.getNumberField().addValueChangeListener(item -> {
            daDialogValidate();
        });

         */
        daDialodAdjustmentAmount.addClickListener(e -> {
            daDialodAdjustmentAmount.setValue(daDialodAdjustmentAmount.getValue()*-1);
        });

        daDialodAdjustmentNote.addValueChangeListener(item -> {
            if(item.getValue()!=null){
                if(driverAdjustmentTemplateList.contains(item.getValue())){
                    daDialodAdjustmentAmount.setValue(item.getValue().getTemplateAmount());
                }
            }
            daDialogValidate();
        });

        daDialodAdjustmentNote.addCustomValueSetListener(event -> {
            daDialodAdjustmentNote.setValue(new DriverAdjustmentTemplate(event.getDetail(),0.0));
            daDialogValidate();
        });

        daDialodAdjustmentDate.addValueChangeListener(item -> {
            daDialogValidate();
            daDialodAdjustmentEndDate.setMin(item.getValue());
        });
        daDialodAdjustmentEndDate.addValueChangeListener(item -> {
            daDialogValidate();
            daDialodAdjustmentDate.setMax(item.getValue());
        });

        daDialodAdjustmentAmount.setWidthFull();
        //TODO:: format Number field with 2 decimals

        VerticalLayout fieldLayout = new VerticalLayout(daDialogDriver,daDialogRangeCreate,daDialodAdjustmentDate,daDialodAdjustmentEndDate,daDialogDOWGroup,daDialodAdjustmentNote,daDialodAdjustmentAmount);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        return fieldLayout;
    }

    private void daDialogCreateMultiMode(Boolean createMulti){
        daDialodAdjustmentDate.setVisible(true);
        daDialodAdjustmentEndDate.setVisible(createMulti);
        daDialogDOWGroup.setVisible(createMulti);
        if(createMulti){
            daDialodAdjustmentDate.setLabel("Range start date");
            daDialodAdjustmentDate.setMax(daDialodAdjustmentEndDate.getValue());
            daDialodAdjustmentEndDate.setMin(daDialodAdjustmentDate.getValue());
        }else{
            daDialodAdjustmentDate.setLabel("Adjustment date");
            daDialodAdjustmentDate.setMax(null);
        }
    }

    private void daDialogClearDOWGroup(){
        daDialogDOWGroup.deselectAll();
    }

    private void daDialogValidate() {
        if(daDialogDriver.isInvalid() || daDialogDriver.getValue()==null){
            daDialogOkButton.setEnabled(false);
        }else if(daDialodAdjustmentNote.isInvalid() || daDialodAdjustmentNote.getValue()==null ){
            daDialogOkButton.setEnabled(false);
        }else if(daDialogRangeCreate.getValue()){
            if(daDialodAdjustmentEndDate.isInvalid() || daDialodAdjustmentEndDate.getValue()==null){
                daDialogOkButton.setEnabled(false);
            }else if(daDialogDOWGroup.getSelectedItems().size() == 0){
                daDialogOkButton.setEnabled(false);
            }else{
                daDialogOkButton.setEnabled(true);
            }
        }else if(!daDialogRangeCreate.getValue()){
            if(daDialodAdjustmentDate.isInvalid() || daDialodAdjustmentDate.getValue()==null){
                daDialogOkButton.setEnabled(false);
            }else{
                daDialogOkButton.setEnabled(true);
            }
        }else{
            daDialogOkButton.setEnabled(true);
        }
        log.info("daDialogValidate: daDialodAdjustmentNote: value:" + daDialodAdjustmentNote.getValue());
    }

    public void daDialogOpen(DriverAdjustment driverAdjustment, DialogMode daDialogMode){
        selectedDriverAdjustment = driverAdjustment;
        daDialogRangeCreate.setValue(false);
        daDialogDriver.setValue(null);
        daDialogClearDOWGroup();
        daDialodAdjustmentDate.setValue(startDate);
        daDialodAdjustmentEndDate.setValue(endDate);
        daDialodAdjustmentDate.setMax(null);
        if(daDialogMode.equals(DialogMode.NEW)){
            daDialogRangeCreate.setVisible(true);
            daDialogOkButton.setText("OK");
            daDialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            daDialogDriver.setReadOnly(false);
            daDialodAdjustmentDate.setReadOnly(false);
            daDialodAdjustmentNote.setReadOnly(false);
            daDialodAdjustmentAmount.setReadOnly(false);
            daDialodAdjustmentNote.setValue(new DriverAdjustmentTemplate());
            daDialodAdjustmentAmount.setValue(0.0);
        }else if(daDialogMode.equals(DialogMode.NEW_FIXED_DRIVER)) {
            daDialogRangeCreate.setVisible(true);
            daDialogOkButton.setText("OK");
            daDialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            daDialogDriver.setReadOnly(true);
            daDialodAdjustmentDate.setReadOnly(false);
            daDialodAdjustmentNote.setReadOnly(false);
            daDialodAdjustmentAmount.setReadOnly(false);
            daDialogDriver.setValue(selectedDriverAdjustment.getDriver());
            daDialodAdjustmentNote.setValue(new DriverAdjustmentTemplate());
            daDialodAdjustmentAmount.setValue(0.0);
        }else if(daDialogMode.equals(DialogMode.DELETE)) {
            daDialogRangeCreate.setVisible(false);
            daDialogCreateMultiMode(Boolean.FALSE);
            daDialogOkButton.setText("DELETE");
            daDialogOkButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            daDialogDriver.setValue(selectedDriverAdjustment.getDriver());
            daDialodAdjustmentDate.setValue(selectedDriverAdjustment.getAdjustmentDate());
            daDialodAdjustmentNote.setValue(selectedDriverAdjustment.getAdjustmentTemplate());
            daDialodAdjustmentAmount.setValue(selectedDriverAdjustment.getAdjustmentAmount());

            daDialogDriver.setReadOnly(true);
            daDialodAdjustmentDate.setReadOnly(true);
            daDialodAdjustmentNote.setReadOnly(true);
            daDialodAdjustmentAmount.setReadOnly(true);
        }else {
            daDialogRangeCreate.setVisible(false);
            daDialogCreateMultiMode(Boolean.FALSE);
            daDialogOkButton.setText("OK");
            daDialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            daDialogDriver.setValue(selectedDriverAdjustment.getDriver());
            daDialogDriver.setReadOnly(true);
            daDialodAdjustmentDate.setReadOnly(false);
            daDialodAdjustmentNote.setReadOnly(false);
            daDialodAdjustmentAmount.setReadOnly(false);
            daDialodAdjustmentDate.setValue(selectedDriverAdjustment.getAdjustmentDate());
            daDialodAdjustmentNote.setValue(selectedDriverAdjustment.getAdjustmentTemplate());
            daDialodAdjustmentAmount.setValue(selectedDriverAdjustment.getAdjustmentAmount());
        }

        daDialogValidate();
        daDialog.open();
    }

    private void daDialogSave(){
        if(daDialogMode.equals(DialogMode.DELETE)){
            driverAdjustmentRepository.delete(selectedDriverAdjustment);
            Notification.show("Deleted");
        }else{
            Integer notifyCounter = 0;
            if(daDialogRangeCreate.getValue()){ //create multiple adjustments
                Stream<LocalDate> dates = daDialodAdjustmentDate.getValue().datesUntil(daDialodAdjustmentEndDate.getValue().plusDays(1));
                List<LocalDate> adjustmentDays = dates.collect(Collectors.toList());
                for (LocalDate adjustmentDay: adjustmentDays) {
                    if(daDialogDOWGroup.getSelectedItems().contains(adjustmentDay.getDayOfWeek())){
                        notifyCounter++;
                        DriverAdjustment driverAdjustment = new DriverAdjustment();
                        driverAdjustment.setFleetId(daDialogDriver.getValue().getFleetId());
                        if(daDialodAdjustmentAmount.getValue()==null){
                            driverAdjustment.setAdjustmentAmount(0.0);
                        }else{
                            driverAdjustment.setAdjustmentAmount(daDialodAdjustmentAmount.getNumberField().getValue());
                        }
                        driverAdjustment.setAdjustmentDate(adjustmentDay);
                        driverAdjustment.setAdjustmentNote(daDialodAdjustmentNote.getValue().getTemplateName());
                        driverAdjustmentRepository.save(driverAdjustment);
                    }
                }
            }else{ //single adjustment creation mode
                notifyCounter = 1;
                selectedDriverAdjustment.setFleetId(daDialogDriver.getValue().getFleetId());
                if(daDialodAdjustmentAmount.getValue()==null){
                    selectedDriverAdjustment.setAdjustmentAmount(0.0);
                }else{
                    selectedDriverAdjustment.setAdjustmentAmount(daDialodAdjustmentAmount.getNumberField().getValue());
                }
                selectedDriverAdjustment.setAdjustmentDate(daDialodAdjustmentDate.getValue());
                selectedDriverAdjustment.setAdjustmentNote(daDialodAdjustmentNote.getValue().getTemplateName());
                driverAdjustmentRepository.save(selectedDriverAdjustment);
            }
            String notifyText = "updated";
            if(daDialogMode.equals(DialogMode.NEW) || daDialogMode.equals(DialogMode.NEW_FIXED_DRIVER)){
                notifyText = "created";
            }
            Notification.show(notifyCounter + " " + notifyText);
        }

        //Refresh
        Boolean fullRefresh = Boolean.FALSE;
        if(daDialogMode.equals(DialogMode.NEW) && !driverPayoutPeriod.getFleetIds().contains(selectedDriverAdjustment.getFleetId())){
            //full page refresh required as the adjustment is for a driver that previously was not listed
            log.info("daDialogSave: new Driver record so refreshing full Driver Payout UI");
            fullRefresh = Boolean.TRUE;
        }else if(daDialogMode.equals(DialogMode.DELETE)){
            //see if this driver still has a DriverPayoutWeek entry
            Long tFleetID = selectedDriverAdjustment.getFleetId();
            driverPayoutPeriod.refresh();
            Boolean driverFound = Boolean.FALSE;
            for (DriverPayoutWeek driverPayoutWeek: driverPayoutPeriod.getDriverPayoutWeekList()) {
                if(driverPayoutWeek.getFleetId().equals(tFleetID)){
                    driverFound = Boolean.TRUE;
                    break;
                }
            }
            if(!driverFound){
                fullRefresh = Boolean.TRUE;
            }
        }

        if(fullRefresh){
            log.info("daDialogSave: refreshing full UI");
            buildDriverPayoutDetails();
        }else{
            refreshDriverPayoutDetails();
        }

        daDialog.close();
    }

    private void refreshTaskFromTookan(DriverPayoutEntity item) {
        //after a confirmation, pull task from the RestAPI and refresh the item
        ConfirmDialog confirmRefreshTask = new ConfirmDialog();
        confirmRefreshTask.setHeader("Confirm Task Refresh");
        confirmRefreshTask.setText("This will update task '" + item.getJobId() + "' directly from Tookan and overwrite any manual edits. \nAre you sure?");
        confirmRefreshTask.setCancelable(true);
        confirmRefreshTask.setCloseOnEsc(true);
        confirmRefreshTask.setConfirmText("Refresh");
        confirmRefreshTask.setConfirmButtonTheme("error primary");
        confirmRefreshTask.addConfirmListener(e -> {
            //call Rest API to refresh item
            RestClientService restClientService = new RestClientService();
            TaskDetailRepository taskDetailRepository = Registry.getBean(TaskDetailRepository.class);
            RestaurantRepository restaurantRepository = Registry.getBean(RestaurantRepository.class);
            OrderDetailRepository orderDetailRepository = Registry.getBean(OrderDetailRepository.class);
            List<Long> itemsToRefresh = new ArrayList<>();
            itemsToRefresh.add(item.getJobId());
            List<TaskDetail> taskDetailList = restClientService.getTaskDetails(itemsToRefresh);
            if(taskDetailList.size() > 0){
                TaskEntity taskEntity = taskDetailList.get(0).getTaskEntity(restaurantRepository,orderDetailRepository,driversRepository);
                if(taskEntity!=null){
                    taskDetailRepository.save(taskEntity);
                    refreshDriverPayoutDetails();
                    Notification.show("Refresh completed");
                }else{
                    Notification.show("Refresh failed...see logs");
                }
            }else{
                Notification.show("Refresh failed...see logs");
            }
        });
        confirmRefreshTask.open();
    }

    private void refreshDriverPayoutDetails(){
        log.info("refreshDriverPayoutDetails: refreshing driver payout period");
        TaskDetailRepository taskDetailRepository = Registry.getBean(TaskDetailRepository.class);

        driverPayoutPeriod.refresh();
        periodBinder.readBean(driverPayoutPeriod);
        if(weekGridMap.containsKey(0L)){
            log.info("Updating adjustments grid for summary");
            weekGridMap.get(0L).setItems(driverPayoutPeriod.getDriverAdjustmentList());
        }
        if(weekGridMap.containsKey(1L)){
            log.info("Updating driver card tips grid for summary");
            weekGridMap.get(1L).setItems(driverPayoutPeriod.getDriverCardTipList());
        }
        if(weekGridMap.containsKey(2L)){
            log.info("Updating driver tip issues grid for summary");
            weekGridMap.get(2L).setItems(driverPayoutPeriod.getTipIssues());
        }
        if(weekGridMap.containsKey(3L)){
            log.info("Updating driver tip issues grid for summary");
            weekGridMap.get(3L).setItems(driverPayoutPeriod.getPayoutDocuments());
        }

        for (DriverPayoutWeek driverPayoutWeek: driverPayoutPeriod.getDriverPayoutWeekList()) {
            Long fleetID = driverPayoutWeek.getFleetId();
            if(weekBinderMap.containsKey(fleetID)){
                weekBinderMap.get(fleetID).readBean(driverPayoutWeek);
            }
            for (DriverPayoutDay driverPayoutDay: driverPayoutWeek.getDriverPayoutDayList()) {
                DriverPayoutPk driverPayoutPk = driverPayoutDay.getDriverPayoutPk();
                if(dayBinderMap.containsKey(driverPayoutPk)){
                    dayBinderMap.get(driverPayoutPk).readBean(driverPayoutDay);
                }
                log.info("refresh: day grids start now");
                if(dayGridMap.containsKey(driverPayoutPk)){
                    dayGridMap.get(driverPayoutPk).setItems(taskDetailRepository.getDriverPayoutByFleetId(driverPayoutDay.getFleetId(), driverPayoutDay.getPayoutDate().atStartOfDay(),driverPayoutDay.getPayoutDate().atTime(23,59,59) ));
                    dayGridMap.get(driverPayoutPk).getDataProvider().refreshAll();
                }
            }
            if(weekGridMap.containsKey(fleetID)){
                log.info("Updating grid for fleetID:" + fleetID);
                weekGridMap.get(fleetID).setItems(driverPayoutWeek.getDriverAdjustmentList());
            }
        }

    }

}
