package ca.admin.delivermore.data.report;

import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.data.entity.RestAdjustment;
import ca.admin.delivermore.data.intuit.Intuit;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.intuit.controller.QBOResult;
import ca.admin.delivermore.data.service.intuit.domain.OAuth2Configuration;
import ca.admin.delivermore.views.UIUtilities;
import ca.admin.delivermore.data.intuit.JournalEntry;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.vaadin.componentfactory.DateRange;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.data.renderer.NumberRenderer;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoIcon;

import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.ConverterTypeVia;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.core.XDocReportException;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.comparators.FixedOrderComparator;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.vaadin.olli.FileDownloadWrapper;

import java.io.*;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

public class RestPayoutSummary implements TaskListRefreshNeededListener {

    private Logger log = LoggerFactory.getLogger(RestPayoutSummary.class);
    //Map<Long, RestPayoutPeriod> restPayoutPeriodMap = new TreeMap<>();
    MultiValuedMap<Long, RestPayoutPeriod> restPayoutPeriodMap = new ArrayListValuedHashMap<>();

    //Map<Long, RestPayoutPeriod> partMonthRestPayoutPeriodMap = new TreeMap<>();
    MultiValuedMap<Long, RestPayoutPeriod> partMonthRestPayoutPeriodMap = new ArrayListValuedHashMap<>();
    List<RestPayoutPeriod> restPayoutPeriodList = new ArrayList<>();
    private List<Restaurant> restaurantList = new ArrayList<>();
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Double sale = 0.0;
    private Double taxes = 0.0;
    private Double totalSale = 0.0;
    private Integer itemCount = 0;

    private Double payoutSale = 0.0;
    private Double payoutTaxes = 0.0;
    private Double payoutTotalSale = 0.0;
    private Integer payoutItemCount = 0;

    private Double paidSale = 0.0;
    private Double paidTotalSale = 0.0;
    private Integer paidItemCount = 0;

    private Double directTotalSale = 0.0;
    private Double phoneInTotalSale = 0.0;
    private Double webOrderTotalSale = 0.0;
    private Double webOrderOnlineTotalSale = 0.0;
    private Integer directItemCount = 0;
    private Integer phoneInItemCount = 0;
    private Integer webOrderItemCount = 0;
    private Integer webOrderOnlineItemCount = 0;

    private Double prePaidTotalSale = 0.0;
    private Double deliveryFeeFromVendor = 0.0;
    private Double deliveryFeeFromExternal = 0.0;
    private Double commissionForPayout = 0.0;
    private Double commissionPerDelivery = 0.0;
    private Double adjustment = 0.0;
    private Double owingToVendor = 0.0;
    private Double COGS = 0.0;

    private VerticalLayout mainLayout = new VerticalLayout();
    private RestaurantRepository restaurantRepository;
    private List<RestAdjustment> restAdjustmentList = new ArrayList<>();
    private List<RestPayoutItem> restPayoutItemList = new ArrayList<>();
    private String[] columnsUpper;
    private String[] columnsUpperAdjustments;

    private List<RestPayoutFromExternalVendor> restPayoutFromExternalVendorList = new ArrayList<>();

    private RestPayoutAdjustmentDialog adjustmentDialog;
    private Details summaryDetails;
    private Grid<RestPayoutPeriod> restGrid;
    private Grid<RestAdjustment> grid;
    private Grid<PayoutDocument> periodDocumentsGrid;
    private VerticalLayout summaryDetailsSummary;
    private VerticalLayout summaryDetailsContent;
    private File csvFile = null;
    private File csvAdjustmentsFile = null;
    private File summaryFile = null;
    private List<PayoutDocument> payoutDocumentList = new ArrayList<>();

    //TODO: move these to Utilities
    private File appPath = new File(System.getProperty("user.dir"));
    private File outputDir = new File(appPath,"tozip");

    private Resource resourcePayStatementTemplate;
    @Autowired
    private EmailService emailService;
    @Autowired
    OAuth2Configuration oAuth2Configuration;
    private Boolean autoLoad = Boolean.TRUE;
    private RestSaleSummary restSaleSummary = new RestSaleSummary();
    private RestSaleSummary saleSummaryPassThru = new RestSaleSummary();
    private RestSaleSummary saleSummaryOther = new RestSaleSummary();

    private MissingGlobalDataDetails missingGlobalDataDetails = new MissingGlobalDataDetails();
    private MissingPOSDataDetails missingPOSDataDetails = new MissingPOSDataDetails();

    private Boolean partMonth = Boolean.FALSE;
    private LocalDate partMonthPayoutPeriodEnd;
    private Double partMonthCOGS = 0.0;
    private Double partMonthTaxes = 0.0;

    private List<ca.admin.delivermore.data.intuit.JournalEntry> journalEntries = new ArrayList<>();

    public RestPayoutSummary(LocalDate periodStart, LocalDate periodEnd, Boolean autoLoad) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.autoLoad = autoLoad;
        //determine if split over 2 months
        if(periodStart.getMonth().equals(periodEnd.getMonth())){
            partMonth = Boolean.FALSE;
            partMonthPayoutPeriodEnd = periodEnd;
        }else{
            partMonth = Boolean.TRUE;
            partMonthPayoutPeriodEnd = periodStart.with(TemporalAdjusters.lastDayOfMonth());
        }
        restaurantRepository = Registry.getBean(RestaurantRepository.class);
        this.resourcePayStatementTemplate = new ClassPathResource("Rest_SummaryPayStatement_Template.docx");
        emailService = Registry.getBean(EmailService.class);
        oAuth2Configuration = Registry.getBean(OAuth2Configuration.class);
        missingGlobalDataDetails.getTaskEditDialog().addListener(this);
        missingPOSDataDetails.getTaskEditDialog().addListener(this);
        mainLayout.setPadding(false);
        mainLayout.setSpacing(false);
        mainLayout.setMargin(false);
        adjustmentDialog = new RestPayoutAdjustmentDialog(periodStart);
        configureRestPayoutItemColumns();
        configureRestAdjustmentsColumns();
        fullRefresh();
    }

    private void fullRefresh(){
        mainLayout.removeAll();
        buildSummary();
        if(autoLoad){
            buildSummaryLayout();
            buildRestaurantLayout();
        }
    }

    public void refresh(){
        updateAdjustment();
        summaryDetailsSummary.removeAll();
        summaryDetailsSummary = buildSummaryDetailsSummary();
        restGrid.getDataProvider().refreshAll();
        grid.getDataProvider().refreshAll();
        payoutDocumentList.clear();
        periodDocumentsGrid.getDataProvider().refreshAll();
    }

    private void buildRestaurantLayout() {
        for (RestPayoutPeriod restPayoutPeriod: restPayoutPeriodList) {
            mainLayout.add(restPayoutPeriod.getMainLayout());
        }
    }

    /*
    * List all vendors that need to be invoiced for refund of delivery fees (example Opa! corporate)
     */
    private VerticalLayout buildExternalInvoiceList() {
        //list external vendors here
        VerticalLayout externalVendorLayout = UIUtilities.getVerticalLayout();
        if(restPayoutFromExternalVendorList.size()>0){
            //Header
            NativeLabel label = new NativeLabel("Invoice needed for external vendors");
            label.getElement().getStyle().set("font-weight", "bold");
            externalVendorLayout.add(label);
            //List of external vendor invoices
            for (RestPayoutFromExternalVendor restPayoutFromExternalVendor: restPayoutFromExternalVendorList) {
                externalVendorLayout.add(RestPayoutPeriod.getExternalVendorItem(restPayoutFromExternalVendor));
            }
        }
        return externalVendorLayout;
    }

    /*
    Build the layout for the summary section
     */
    private void buildSummaryLayout() {
        summaryDetails = UIUtilities.getDetails();
        //summaryDetails.setSizeUndefined();
        mainLayout.add(summaryDetails);
        summaryDetailsSummary = buildSummaryDetailsSummary();
        summaryDetailsContent = UIUtilities.getVerticalLayout();
        summaryDetailsContent.add(buildExternalInvoiceList());
        summaryDetailsContent.add(buildSummaryDocuments());
        summaryDetailsContent.add(missingGlobalDataDetails.buildMissingGlobalData(periodStart,periodEnd));
        summaryDetailsContent.add(missingPOSDataDetails.buildMissingPOSData(periodStart,periodEnd));
        summaryDetailsContent.add(buildSummaryLayoutContentRestList());
        summaryDetailsContent.add(buildSummaryLayoutContentAdjustmentsList());
        summaryDetails.add(summaryDetailsContent);

    }

    private Details buildSummaryDocuments() {
        //Add the Documents review Details pane
        List<PayoutDocument> selectedPayoutDocuments = new ArrayList<>();
        Details periodDocuments = new Details("Documents");
        periodDocuments.addThemeVariants(DetailsVariant.FILLED);
        periodDocuments.setWidthFull();
        periodDocumentsGrid = new Grid<>();

        HorizontalLayout periodDocumentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);

        Button postJournalEntries = new Button("Post to QBO");
        postJournalEntries.setDisableOnClick(true);
        postJournalEntries.setEnabled(false);
        postJournalEntries.addClickListener(e -> {
            for (JournalEntry journalEntry:journalEntries) {
                QBOResult qboResult = journalEntry.post();
                log.info("buildSummaryDocuments: qboResult:" + qboResult.getMessage());
                if(qboResult.getSuccess()){
                    Notification.show("Journal Entry:" + journalEntry.getDocNumber() + " posted");
                }else{
                    Notification.show("Failed to post Journal Entry:" + journalEntry.getDocNumber());
                }
            }
        });

        Button createDocuments = new Button("Create documents");
        createDocuments.setDisableOnClick(true);
        createDocuments.addClickListener(e -> {
            payoutDocumentList = getPayoutDocuments();
            periodDocumentsGrid.setItems(payoutDocumentList);
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
                    String subject = "DeliverMore " + payoutDocument.getName();
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
                new StreamResource("VendorPayoutFiles.zip", () -> {
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
        //periodDocumentsGrid.setAllRowsVisible(true);
        periodDocumentsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        //periodDocumentsGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        periodDocumentsGrid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        periodDocumentsGrid.setItems(payoutDocumentList);
        periodDocumentsGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        periodDocumentsGrid.addComponentColumn(item -> {
            Anchor anchor = new Anchor(item.getResource(), item.getName());
            anchor.setTarget("_blank");
            return anchor;
        })
                .setFlexGrow(1)
                .setHeader("Document Link");
        periodDocumentsGrid.addColumn(PayoutDocument::getEmailPresentation)
                .setFlexGrow(0)
                .setWidth("250px")
                .setHeader("Email");
        //periodDocumentsGrid.getColumns().forEach(col -> col.setAutoWidth(true));
        periodDocumentsGrid.addSelectionListener(selection -> {
            selectedPayoutDocuments.clear();
            selectedPayoutDocuments.addAll(selection.getAllSelectedItems());
            emailDocumentsButton.setEnabled(false);
            zipDocumentsButton.setEnabled(false);
            for (PayoutDocument payoutDocument: selection.getAllSelectedItems()) {
                zipDocumentsButton.setEnabled(true);
                if(!payoutDocument.getEmailAddress().isEmpty()){
                    emailDocumentsButton.setEnabled(true);
                    break;
                }
            }
        });
        return periodDocuments;
    }

    public File getZippedFileforFileList(List<PayoutDocument> docList){
        //log.info("***Start of ZIP process***");
        String zipFileName = "Start " + periodStart;
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
        //log.info("***END of ZIP process***");

        return zippedFile;

    }

    private void createJournalEntries(){
        journalEntries.clear();
        LocalDate journalEndDate;
        if(partMonth){
            journalEndDate = partMonthPayoutPeriodEnd;
        }else{
            journalEndDate = periodEnd;
        }
        String prefix = "VendorPay_";
        String prefixMemo = "Vendor payout";
        String prefixFile = "VendorPayoutJournalEntry-";
        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setDocNumber(prefix, journalEndDate);
        journalEntry.setTxnDate(journalEndDate);
        journalEntry.setPrivateNote(prefixMemo, periodStart,journalEndDate);
        journalEntry.setFileName(prefixFile, periodStart,journalEndDate);
        log.info("createJournalEntries: journalNo:" + journalEntry.getDocNumber());
        if(partMonth){
            //build first part of period
            log.info("createJournalEntries: split JE - part 1");
            journalEntry.addLine(getPartMonthCOGS(), JournalEntry.PostingType.Debit,"COGS Sales","");
            journalEntry.addLine(getPartMonthTaxes(), JournalEntry.PostingType.Debit,"Sales Tax Payable","");
            for (Restaurant restaurant : restaurantList) {
                if(partMonthRestPayoutPeriodMap.containsKey(restaurant.getRestaurantId())){
                    String restName = restaurant.getName();
                    Collection<RestPayoutPeriod> payoutPeriods = partMonthRestPayoutPeriodMap.get(restaurant.getRestaurantId());
                    Double owing = 0.0;
                    Double paidToVendor = 0.0;
                    for (RestPayoutPeriod payoutPeriod: payoutPeriods) {
                        owing+= payoutPeriod.getOwingToVendor();
                        paidToVendor+= payoutPeriod.getPaidToVendor();
                    }
                    if(owing>0){
                        journalEntry.addLine(owing, JournalEntry.PostingType.Credit,"Chequing","", Intuit.EntityType.Vendor,restName);
                    }
                    if(paidToVendor>0){
                        journalEntry.addLine(paidToVendor, JournalEntry.PostingType.Credit,"Wise Card Account","", Intuit.EntityType.Vendor,restName);
                    }
                }
            }
            //log.info("createJournalEntries: split JE - part 1 detail:" + journalEntry.formattedString());
            journalEntries.add(journalEntry);
            //build second part of period
            log.info("createJournalEntries: split JE - part 2");
            JournalEntry journalEntry2 = new JournalEntry();
            journalEntry2.setDocNumber(prefix, periodEnd);
            journalEntry2.setTxnDate(periodEnd);
            journalEntry2.setPrivateNote(prefixMemo, partMonthPayoutPeriodEnd.plusDays(1L),periodEnd);
            journalEntry2.setFileName(prefixFile, partMonthPayoutPeriodEnd.plusDays(1L),periodEnd);
            journalEntry2.addLine(Utility.getInstance().round(getCOGS() - getPartMonthCOGS(),2), JournalEntry.PostingType.Debit,"COGS Sales","");
            journalEntry2.addLine(Utility.getInstance().round(getPayoutTaxes() - getPartMonthTaxes(),2), JournalEntry.PostingType.Debit,"Sales Tax Payable","");
            for (Restaurant restaurant : restaurantList) {
                String restName = restaurant.getName();
                if(restPayoutPeriodMap.containsKey(restaurant.getRestaurantId())){
                    //TODO: CHANGED to handle possible multiple periods
                    Collection<RestPayoutPeriod> payoutPeriods = restPayoutPeriodMap.get(restaurant.getRestaurantId());
                    Double owing = 0.0;
                    Double paidToVendor = 0.0;
                    for (RestPayoutPeriod payoutPeriod: payoutPeriods) {
                        owing+= payoutPeriod.getOwingToVendor();
                        paidToVendor+= payoutPeriod.getPaidToVendor();
                    }
                    Double partMonthOwingToVendor = 0.0;
                    Double partMonthPaidToVendor = 0.0;
                    if(partMonthRestPayoutPeriodMap.containsKey(restaurant.getRestaurantId())){
                        Collection<RestPayoutPeriod> partMonthPayoutPeriods = partMonthRestPayoutPeriodMap.get(restaurant.getRestaurantId());
                        for (RestPayoutPeriod payoutPeriod: partMonthPayoutPeriods) {
                            partMonthOwingToVendor+= payoutPeriod.getOwingToVendor();
                            partMonthPaidToVendor+= payoutPeriod.getPaidToVendor();
                        }
                    }
                    if(owing-partMonthOwingToVendor>0){
                        journalEntry2.addLine(Utility.getInstance().round(owing-partMonthOwingToVendor,2), JournalEntry.PostingType.Credit,"Chequing","", Intuit.EntityType.Vendor,restName);
                    }
                    if(paidToVendor-partMonthPaidToVendor>0){
                        journalEntry2.addLine(Utility.getInstance().round(paidToVendor-partMonthPaidToVendor,2), JournalEntry.PostingType.Credit,"Wise Card Account","", Intuit.EntityType.Vendor,restName);
                    }
                }
            }
            //log.info("createJournalEntries: split JE - part 2 detail:" + journalEntry2.formattedString());
            journalEntries.add(journalEntry2);
        }else{
            journalEntry.addLine(getCOGS(), JournalEntry.PostingType.Debit,"COGS Sales","");
            journalEntry.addLine(getPayoutTaxes(), JournalEntry.PostingType.Debit,"Sales Tax Payable","");
            //log.info("createJournalEntries: COGS Sales:" + getCOGS() + " Tax:" + getPayoutTaxes());
            for (Restaurant restaurant : restaurantList) {
                String restName = restaurant.getName();
                if(restPayoutPeriodMap.containsKey(restaurant.getRestaurantId())){
                    Collection<RestPayoutPeriod> payoutPeriods = restPayoutPeriodMap.get(restaurant.getRestaurantId());
                    Double owing = 0.0;
                    Double paidToVendor = 0.0;
                    for (RestPayoutPeriod payoutPeriod: payoutPeriods) {
                        owing+= payoutPeriod.getOwingToVendor();
                        paidToVendor+= payoutPeriod.getPaidToVendor();
                    }
                    if(owing>0){
                        journalEntry.addLine(owing, JournalEntry.PostingType.Credit,"Chequing","", Intuit.EntityType.Vendor,restName);
                        //log.info("createJournalEntries: restaurant:" + restName + " CREDIT owing:" + owing);
                    }
                    if(paidToVendor>0){
                        journalEntry.addLine(paidToVendor, JournalEntry.PostingType.Credit,"Wise Card Account","", Intuit.EntityType.Vendor,restName);
                        //log.info("createJournalEntries: restaurant:" + restName + " CREDIT paidToVendor:" + paidToVendor);
                    }
                }
            }
            //log.info("createJournalEntries: detail:" + journalEntry.formattedString());
            journalEntries.add(journalEntry);
        }
    }

    private List<PayoutDocument> getPayoutDocuments(){
        payoutDocumentList.clear();
        Utility.emptyDir(outputDir);
        try {
            Files.createDirectory(outputDir.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //create and save a journalEntry
        if(oAuth2Configuration.isConfigured()){
            createJournalEntries();
            for (JournalEntry journalEntry: journalEntries) {
                String journalFileName = journalEntry.getFileName();
                File journalFile = new File(outputDir,journalFileName);

                QBOResult qboResult = journalEntry.save(journalFile);
                if(qboResult.getSuccess()){
                    payoutDocumentList.add(new PayoutDocument(journalFileName, journalFile, ""));
                }else{
                    log.info("getPayoutDocuments: error saving journalEntry to file:" + journalFileName);
                }
            }
        }

        createSummaryStatement();
        String docName = "Summary Payout Statement: " + getPeriodStart() + " - " + getPeriodEnd();
        payoutDocumentList.add(new PayoutDocument(docName, summaryFile,""));

        Integer count = 0;
        for (RestPayoutPeriod restPayoutPeriod: restPayoutPeriodList) {
            count++;
            restPayoutPeriod.createStatement();
            docName = "Statement for:" + restPayoutPeriod.getRestaurantName() + " : " + restPayoutPeriod.getPeriodRange();
            payoutDocumentList.add(new PayoutDocument(docName, restPayoutPeriod.getPdfFile(),restPayoutPeriod.getRestaurantEmail()));
            //remove the break after testing
            //if(count>6) break;
        }

        //save the csv source
        String csvFileName = "VendorPayoutDetails-" + getPeriodStart() + "-" + getPeriodEnd() + ".csv";
        csvFile = new File(outputDir,csvFileName);
        saveCSV(csvFile);
        if(csvFile!=null){
            payoutDocumentList.add(new PayoutDocument("All vendor payout tasks (Excel)", csvFile, ""));
        }

        //save the adjustments to csvAdjustmentsFile
        if(getRestAdjustmentList().size()>0){
            csvFileName = "VendorPayoutAdjustments-" + getPeriodStart() + "-" + getPeriodEnd() + ".csv";
            csvAdjustmentsFile = new File(outputDir,csvFileName);
            saveAdjustmentsCSV(csvAdjustmentsFile);
            if(csvAdjustmentsFile!=null){
                payoutDocumentList.add(new PayoutDocument("All vendor adjustments (Excel)", csvAdjustmentsFile, ""));
            }
        }

        return payoutDocumentList;
    }

    private void saveCSV(File csvFile){

        try {
            var mappingStrategy = new HeaderColumnNameMappingStrategy<RestPayoutItem>();
            mappingStrategy.setType(RestPayoutItem.class);
            mappingStrategy.setColumnOrderOnWrite(new FixedOrderComparator<>(columnsUpper));

            Stream<RestPayoutItem> restPayoutItemStream = restPayoutItemList.stream();
            StringWriter output = new StringWriter();
            StatefulBeanToCsv<RestPayoutItem> beanToCsv = new StatefulBeanToCsvBuilder<RestPayoutItem>(output)
                    .withMappingStrategy(mappingStrategy)
                    .build();
            beanToCsv.write(restPayoutItemStream);
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

    private void saveAdjustmentsCSV(File csvFile){

        try {
            var mappingStrategy = new HeaderColumnNameMappingStrategy<RestAdjustment>();
            mappingStrategy.setType(RestAdjustment.class);
            mappingStrategy.setColumnOrderOnWrite(new FixedOrderComparator<>(columnsUpperAdjustments));

            Stream<RestAdjustment> restAdjustmentsStream = restAdjustmentList.stream();
            StringWriter output = new StringWriter();
            StatefulBeanToCsv<RestAdjustment> beanToCsv = new StatefulBeanToCsvBuilder<RestAdjustment>(output)
                    .withMappingStrategy(mappingStrategy)
                    .build();
            beanToCsv.write(restAdjustmentsStream);
            var content = output.toString();
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile));
            writer.write(content);

            writer.close();
        } catch (CsvDataTypeMismatchException | CsvRequiredFieldEmptyException ex) {
            log.info("saveAdjustmentsCSV: CSV write failed");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void configureRestPayoutItemColumns() {
        columnsUpper = new String[]{
                "restaurantId",
                "restaurantName",
                "orderId",
                "itemType",
                "saleType",
                "creationDateTime",
                "sale",
                "taxes",
                "totalSale",
                "prePaidTotalSale",
                "deliveryFee",
                "deliveryFeeFromVendor",
                "paymentMethod",
                "paidToVendor",
                "commissionPerDelivery"
        };

        for(int i=0;i<columnsUpper.length;i++){
            columnsUpper[i] = columnsUpper[i].toUpperCase();
        }
    }

    private void configureRestAdjustmentsColumns() {
        columnsUpperAdjustments = new String[]{
                "Id",
                "restaurantId",
                "restaurantName",
                "adjustmentDate",
                "adjustmentNote",
                "adjustmentAmount"
        };

        for(int i=0;i<columnsUpperAdjustments.length;i++){
            columnsUpperAdjustments[i] = columnsUpperAdjustments[i].toUpperCase();
        }
    }

    private void createSummaryStatement() {
        log.info("createPDFStatements: summary statement");
        String outputFileExt = ".pdf";

        String outputFileName = "SummarySalesStatement-" + periodStart + "-" + periodEnd;
        File outputFile = new File(outputDir,outputFileName + outputFileExt);
        summaryFile = outputFile;

        try {
            // 1) Load Docx file by filling Velocity template engine and cache it to the registry
            InputStream in = resourcePayStatementTemplate.getInputStream();
            IXDocReport report = XDocReportRegistry.getRegistry().loadReport(in, TemplateEngineKind.Velocity);

            // 2) Create context Java model
            IContext context = report.createContext();
            context.put("restPayoutSummary", this);

            FieldsMetadata metadata = new FieldsMetadata();
            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            metadata.addFieldAsList("restAdjustmentList.getRestaurantName()");
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentDateFmt()");
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentNote()");
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentAmountFmt()");

            context.put("restAdjustmentList", restAdjustmentList);

            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            metadata.addFieldAsList("restPayoutPeriodList.getRestaurantName()");
            metadata.addFieldAsList("restPayoutPeriodList.getPeriodRange()");
            metadata.addFieldAsList("restPayoutPeriodList.getPayoutItemCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getPaidItemCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getDirectSalesCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getPhoneInSalesCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getWebOrderSalesCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getWebOrderOnlineSalesCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getItemCount()");
            metadata.addFieldAsList("restPayoutPeriodList.getPayoutTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getPaidTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getDirectTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getPhoneInTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getWebOrderTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getWebOrderOnlineTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getTotalSale()");
            metadata.addFieldAsList("restPayoutPeriodList.getOwingToVendor()");
            report.setFieldsMetadata(metadata);

            context.put("restPayoutPeriodList", restPayoutPeriodList);
            /*
            List<RestPayoutItem> restPaidItems = this.paidRestItems;
            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            metadata.addFieldAsList("restPaidItems.getCreationDateTimeFmt()");
            metadata.addFieldAsList("restPaidItems.getOrderId()");
            metadata.addFieldAsList("restPaidItems.getSale()");
            metadata.addFieldAsList("restPaidItems.getTaxes()");
            metadata.addFieldAsList("restPaidItems.getTotalSale()");
            metadata.addFieldAsList("restPaidItems.getDeliveryFee()");
            metadata.addFieldAsList("restPaidItems.getDeliveryFeeFromVendor()");
            metadata.addFieldAsList("restPaidItems.getPaymentMethod()");
            report.setFieldsMetadata(metadata);

            context.put("restPaidItems", restPaidItems);

             */

            // 3) Generate report by merging Java model with the Docx
            //To PDF
            OutputStream out = new FileOutputStream(outputFile);
            Options options = Options.getTo(ConverterTypeTo.PDF).via(ConverterTypeVia.XWPF);
            report.convert(context, options, out);
        } catch (IOException e) {
            log.info("createPDFStatements: FAILED vendor summary:" + " ERROR:" + e.toString());
            e.printStackTrace();
        } catch (XDocReportException e) {
            log.info("createPDFStatements: FAILED2 vendor summary:" + " ERROR:" + e.toString());
            e.printStackTrace();
        }

    }

    private VerticalLayout buildSummaryDetailsSummary(){
        VerticalLayout summaryDetailsSummary = UIUtilities.getVerticalLayout();
        HorizontalLayout summaryDetailsSummaryHeader = UIUtilities.getHorizontalLayout();
        HorizontalLayout summaryDetailsSummaryOwing = UIUtilities.getHorizontalLayoutNoWidthCentered();
        HorizontalLayout summaryDetailsSummaryFields = UIUtilities.getHorizontalLayout();
        HorizontalLayout summaryDetailsSummaryFields2 = UIUtilities.getHorizontalLayout();
        String summaryTitle = "Payout Summary: " + periodStart + " - " + periodEnd;
        NumberField summaryOwing = UIUtilities.getNumberField("", getOwingToVendor());
        NativeLabel summaryOwingLabel = new NativeLabel("Owing :");
        summaryDetailsSummaryOwing.add(summaryOwingLabel,summaryOwing);
        summaryDetailsSummaryHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        summaryDetailsSummaryHeader.setAlignItems(FlexComponent.Alignment.START);
        summaryDetailsSummaryHeader.add(new Text(summaryTitle),summaryDetailsSummaryOwing);
        summaryDetailsSummary.add(summaryDetailsSummaryHeader,summaryDetailsSummaryFields,summaryDetailsSummaryFields2);
        summaryDetails.setSummary(summaryDetailsSummary);
        NumberField summaryCOGS = UIUtilities.getNumberField("COGS", getCOGS());
        NumberField summaryPayoutSale = UIUtilities.getNumberField("Sales", getPayoutSale());
        NumberField summaryPayoutTaxes = UIUtilities.getNumberField("Taxes", getPayoutTaxes());
        NumberField summaryPayoutTotalSale = UIUtilities.getNumberField("TotalSales", getPayoutTotalSale());
        TextField summaryItemCount = UIUtilities.getTextFieldRO("Count", getPayoutItemCount().toString(),"100px");
        NumberField summaryDeliveryFeeFromVendor = UIUtilities.getNumberField("Fee from Vendor",getDeliveryFeeFromVendor());
        NumberField summaryCommission = UIUtilities.getNumberField("Commission", getCommissionForPayout());
        NumberField summaryCommissionPerDelivery = UIUtilities.getNumberField("Commission Per", getCommissionPerDelivery());
        NumberField summaryPrePaidTotalSale = UIUtilities.getNumberField("Pre Paid Sale", getPrePaidTotalSale());
        NumberField summaryAdjustment = UIUtilities.getNumberField("Adjustment", getAdjustment());
        summaryDetailsSummaryFields.add(
                summaryPayoutSale,
                summaryPayoutTaxes,
                summaryPayoutTotalSale,
                summaryItemCount,
                summaryCOGS
                );
        summaryDetailsSummaryFields2.add(
                summaryDeliveryFeeFromVendor,
                summaryCommission,
                summaryCommissionPerDelivery,
                summaryPrePaidTotalSale,
                summaryAdjustment
        );
        return summaryDetailsSummary;
    }

    public Details buildSummaryLayoutContentRestList(){
        Details gridDetails = UIUtilities.getDetails();
        gridDetails.setSummaryText("Restaurant Payouts");
        gridDetails.setOpened(true);
        restGrid = new Grid<>();
        gridDetails.add(restGrid);
        restGrid.setItems(restPayoutPeriodList);
        restGrid.setWidthFull();
        restGrid.setAllRowsVisible(true);
        restGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        restGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        restGrid.setColumnReorderingAllowed(true);
        restGrid.addColumn(RestPayoutPeriod::getRestaurantName)
                .setHeader("Restaurant")
                .setKey("restaurant")
                .setWidth("175px")
                .setFlexGrow(0)
                .setResizable(true)
                .setSortable(true)
                .setFrozen(true);
        String numberColWidth = "100px";
        restGrid.addColumn(new NumberRenderer<>(RestPayoutPeriod::getOwingToVendor,new DecimalFormat("##0.00")))
                .setComparator(RestPayoutPeriod::getOwingToVendor)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("Owing").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(RestPayoutPeriod::getPeriodRange)
                .setHeader("Period")
                .setFlexGrow(1);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getCOGS()))
                .setComparator(RestPayoutPeriod::getCOGS)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("COGS").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getPayoutSale()))
                .setComparator(RestPayoutPeriod::getPayoutSale)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("Sales").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getPayoutTaxes()))
                .setComparator(RestPayoutPeriod::getPayoutTaxes)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setHeader("Taxes").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getPayoutTotalSale()))
                .setComparator(RestPayoutPeriod::getPayoutTotalSale)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("TotalSales").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> item.getPayoutItemCount())
                .setComparator(RestPayoutPeriod::getPayoutItemCount)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("Count").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDeliveryFeeFromVendor()))
                .setComparator(RestPayoutPeriod::getDeliveryFeeFromVendor)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setHeader("FeeFromVendor").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getCommissionForPayout()))
                .setComparator(RestPayoutPeriod::getCommissionForPayout)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("Commission").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getCommissionPerDelivery()))
                .setComparator(RestPayoutPeriod::getCommissionPerDelivery)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setHeader("Comm Per").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getPrePaidTotalSale()))
                .setComparator(RestPayoutPeriod::getPrePaidTotalSale)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setHeader("Pre Paid Sale").setTextAlign(ColumnTextAlign.END);
        restGrid.addColumn(item -> UIUtilities.getNumberFormatted(item.getAdjustment()))
                .setComparator(RestPayoutPeriod::getAdjustment)
                .setWidth(numberColWidth)
                .setFlexGrow(0)
                .setSortable(true)
                .setHeader("Adjustment").setTextAlign(ColumnTextAlign.END);

        return gridDetails;
    }

    private Details buildSummaryLayoutContentAdjustmentsList(){
        Details gridDetails = UIUtilities.getDetails();
        gridDetails.setSummaryText("All Adjustments");
        gridDetails.setOpened(true);
        grid = new Grid<>();
        grid.setItems(restAdjustmentList);
        VerticalLayout adjustmentsLayout = UIUtilities.getVerticalLayout();
        HorizontalLayout periodAdustmentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);
        Icon addNewIcon = LumoIcon.PLUS.create();
        addNewIcon.setColor("green");
        Button adjustmentsAddNew = new Button("Add", addNewIcon);
        adjustmentsAddNew.addThemeVariants(ButtonVariant.LUMO_SMALL);
        adjustmentsAddNew.addClickListener(e -> {
            adjustmentDialog.setDialogMode(RestPayoutAdjustmentDialog.DialogMode.NEW);
            adjustmentDialog.dialogOpen(new RestAdjustment(), this, null);
        });
        periodAdustmentsToolbar.add(adjustmentsAddNew);
        adjustmentsLayout.add(periodAdustmentsToolbar);
        gridDetails.add(adjustmentsLayout);
        adjustmentsLayout.add(grid);
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setColumnReorderingAllowed(true);
        grid.addComponentColumn(item -> {
            Icon editIcon = LumoIcon.EDIT.create();
            //Button editButton = new Button("Edit");
            editIcon.addClickListener(e -> {
                adjustmentDialog.setDialogMode(RestPayoutAdjustmentDialog.DialogMode.EDIT);
                adjustmentDialog.dialogOpen(item,this,getPeriod(item.getRestaurantId()));
            });
            return editIcon;
        }).setWidth("150px").setFlexGrow(0);
        grid.addComponentColumn(item -> {
            Icon deleteIcon = LumoIcon.CROSS.create();
            deleteIcon.setColor("red");
            deleteIcon.addClickListener(e -> {
                adjustmentDialog.setDialogMode(RestPayoutAdjustmentDialog.DialogMode.DELETE);
                adjustmentDialog.dialogOpen(item, this, getPeriod(item.getRestaurantId()));
            });
            return deleteIcon;
        }).setWidth("150px").setFlexGrow(0);
        grid.addColumn(RestAdjustment::getRestaurantName)
                .setFlexGrow(0)
                .setHeader("Restaurant");
        grid.addColumn(new LocalDateRenderer<>(RestAdjustment::getAdjustmentDate,"MM/dd"))
                .setSortable(true)
                .setFlexGrow(0)
                .setHeader("Date");
        grid.addColumn(RestAdjustment::getAdjustmentNote)
                .setFlexGrow(1)
                .setHeader("Note");
        grid.addColumn(new NumberRenderer<>(RestAdjustment::getAdjustmentAmount,new DecimalFormat("##0.00")))
                .setComparator(RestAdjustment::getAdjustmentAmount)
                .setFlexGrow(0)
                .setHeader("Amount").setTextAlign(ColumnTextAlign.END);
        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        return gridDetails;
    }

    private void buildSummary() {

        //determine which restaurants to process for given start date
        this.restPayoutItemList.clear();
        if(autoLoad){
            restaurantList = restaurantRepository.getEffectiveRestaurantsForPayout(periodStart);
        }else{
            restaurantList = restaurantRepository.findDistinctNonExpiredRestaurants();
        }
        for (Restaurant restaurant: restaurantList) {
            DateRange range;
            List<RestPayoutPeriod> allPeriods = new ArrayList<>();
            if(autoLoad){
                range = findRestPeriodDateRange(restaurant.getStartDayOffset(), restaurant.getWeeksInPeriod(), restaurant.getRangeStartDate());
            }else{
                range = new DateRange(periodStart, periodEnd);
            }
            if(range==null){
                log.info("buildSummary:" + restaurant.getName() + " Not a valid payout period");
            }else{
                if(autoLoad){
                    buildPeriod(restaurant,range);
                }else{
                    List<Restaurant> allRestSettings = restaurantRepository.findByRestaurantId(restaurant.getRestaurantId());
                    //log.info("buildSummary:" + restaurant.getName() + " count of settings:" + allRestSettings.size());

                    if(allRestSettings.size()==1){  //if only 1 then just build it
                        //log.info("  **: only one setting: pStart:" + range.getStartDate() + " pEnd:" + range.getEndDate());
                        buildPeriod(restaurant,range);
                    }else{
                        for (Restaurant restSetting: allRestSettings) {
                            //check if the setting is in effect
                            if(restSetting.getDateExpired()!=null && restSetting.getDateExpired().isBefore(range.getStartDate())){
                                //log.info("  **: expired BEFORE: pStart:" + range.getStartDate() + " pEnd:" + range.getEndDate() + " sEffective:" + restSetting.getDateEffective() + " sExpiry:" + restSetting.getDateExpired());
                                continue; //skip this one as it's expired
                            }else if(restSetting.getDateEffective().isAfter(range.getEndDate())){
                                //log.info("  **: effective AFTER: pStart:" + range.getStartDate() + " pEnd:" + range.getEndDate() + " sEffective:" + restSetting.getDateEffective() + " sExpiry:" + restSetting.getDateExpired());
                                continue; //skip as this one is not yet in effect
                            }
                            //this setting should be in effect - determine the start/end
                            LocalDate thisStart;
                            LocalDate thisEnd;
                            if(restSetting.getDateEffective().isBefore(range.getStartDate())){
                                thisStart = range.getStartDate();
                            }else{
                                thisStart = restSetting.getDateEffective();
                            }
                            if(restSetting.getDateExpired()==null || restSetting.getDateExpired().isAfter(range.getEndDate())){
                                thisEnd = range.getEndDate();
                            }else{
                                thisEnd = restSetting.getDateExpired();
                            }
                            //log.info("  **: within period: pStart:" + range.getStartDate() + " pEnd:" + range.getEndDate() + " sEffective:" + restSetting.getDateEffective() + " sExpiry:" + restSetting.getDateExpired());
                            //log.info("  **: within period: thisStart:" + thisStart + " thisEnd:" + thisEnd);
                            buildPeriod(restSetting,new DateRange(thisStart,thisEnd));
                        }
                    }
                }
            }
        }
        //need to sort the list for use in grids and restaurant lists
        //log.info("buildSummary: restPayoutPeriodMap: size:" + restPayoutPeriodMap.size());
        Collection<RestPayoutPeriod> restPayoutPeriodCol = restPayoutPeriodMap.values();
        //log.info("buildSummary: restPayoutPeriodCol: size:" + restPayoutPeriodCol.size());
        restPayoutPeriodList = new ArrayList<>(restPayoutPeriodCol);
        //log.info("buildSummary: restPayoutPeriodList: size:" + restPayoutPeriodList.size());
        Collections.sort(restPayoutPeriodList, Comparator.comparing(RestPayoutPeriod::getRestaurantName));

    }

    private void buildPeriod(Restaurant restaurant, DateRange range){
        RestPayoutPeriod restPayoutPeriod = new RestPayoutPeriod(range.getStartDate(), range.getEndDate(), restaurant, this);

        restPayoutPeriodMap.put(restaurant.getRestaurantId(), restPayoutPeriod);
        log.info("buildRestPayoutDetails:" + restaurant.getName() + " start:" + range.getStartDate() + " end:" + range.getEndDate() + " PayoutSale:" + restPayoutPeriod.getPayoutSale() + " PayoutTaxes:" + restPayoutPeriod.getPayoutTaxes() + " PayoutTotalSale:" + restPayoutPeriod.getPayoutTotalSale() + " CommissionForPayout:" + restPayoutPeriod.getCommissionForPayout() + " CommissionPerDelivery:" + restPayoutPeriod.getCommissionPerDelivery() + " DeliveryFeeFromVendor:" + restPayoutPeriod.getDeliveryFeeFromVendor() + " owingToVendor:" + restPayoutPeriod.getOwingToVendor() + " commissionRate:" + restaurant.getCommissionRate() );
        if(partMonth){
            RestPayoutPeriod partMonthRestPayoutPeriod = new RestPayoutPeriod(range.getStartDate(), partMonthPayoutPeriodEnd, restaurant, this);
            partMonthRestPayoutPeriodMap.put(restaurant.getRestaurantId(), partMonthRestPayoutPeriod);
            this.partMonthTaxes = this.partMonthTaxes + partMonthRestPayoutPeriod.getPayoutTaxes();
            this.partMonthCOGS = this.partMonthCOGS + partMonthRestPayoutPeriod.getCOGS();
        }

        this.payoutSale = this.payoutSale + restPayoutPeriod.getPayoutSale();
        this.payoutTaxes = this.payoutTaxes + restPayoutPeriod.getPayoutTaxes();
        this.payoutTotalSale = this.payoutTotalSale + restPayoutPeriod.getPayoutTotalSale();
        this.payoutItemCount = this.payoutItemCount + restPayoutPeriod.getPayoutItemCount();
        this.paidSale = this.paidSale + restPayoutPeriod.getPaidSale();
        this.paidTotalSale = this.paidTotalSale + restPayoutPeriod.getPaidTotalSale();
        this.paidItemCount = this.paidItemCount + restPayoutPeriod.getPaidItemCount();
        this.directTotalSale = this.directTotalSale + restPayoutPeriod.getDirectTotalSale();
        this.directItemCount = this.directItemCount + restPayoutPeriod.getDirectSalesCount();
        this.phoneInTotalSale = this.phoneInTotalSale + restPayoutPeriod.getPhoneInTotalSale();
        this.webOrderTotalSale = this.webOrderTotalSale + restPayoutPeriod.getWebOrderTotalSale();
        this.webOrderOnlineTotalSale = this.webOrderOnlineTotalSale + restPayoutPeriod.getWebOrderOnlineTotalSale();
        this.phoneInItemCount = this.phoneInItemCount + restPayoutPeriod.getPhoneInSalesCount();
        this.webOrderItemCount = this.webOrderItemCount + restPayoutPeriod.getWebOrderSalesCount();
        this.webOrderOnlineItemCount = this.webOrderOnlineItemCount + restPayoutPeriod.getWebOrderOnlineSalesCount();
        this.sale = this.sale + restPayoutPeriod.getSale();
        this.taxes = this.taxes + restPayoutPeriod.getTaxes();
        this.totalSale = this.totalSale + restPayoutPeriod.getTotalSale();
        this.prePaidTotalSale = this.prePaidTotalSale + restPayoutPeriod.getPrePaidTotalSale();
        this.itemCount = this.itemCount + restPayoutPeriod.getItemCount();
        this.adjustment = this.adjustment + restPayoutPeriod.getAdjustment();
        this.restAdjustmentList.addAll(restPayoutPeriod.getRestAdjustmentList());
        this.restPayoutItemList.addAll(restPayoutPeriod.getPayoutRestItems());
        this.restPayoutItemList.addAll(restPayoutPeriod.getPaidRestItems());
        this.restPayoutItemList.addAll(restPayoutPeriod.getCancelledRestItems());
        this.commissionForPayout = this.commissionForPayout + restPayoutPeriod.getCommissionForPayout();
        this.commissionPerDelivery = this.commissionPerDelivery + restPayoutPeriod.getCommissionPerDelivery();
        this.deliveryFeeFromExternal = this.deliveryFeeFromExternal + restPayoutPeriod.getDeliveryFeeFromExternal();
        this.deliveryFeeFromVendor = this.deliveryFeeFromVendor + restPayoutPeriod.getDeliveryFeeFromVendor();
        this.owingToVendor = this.owingToVendor + restPayoutPeriod.getOwingToVendor();
        this.COGS = this.COGS + restPayoutPeriod.getCOGS();

        updateSaleSummary(this.restSaleSummary,restPayoutPeriod.getRestSaleSummary());
        updateSaleSummary(this.saleSummaryPassThru,restPayoutPeriod.getSaleSummaryPassThru());
        updateSaleSummary(this.saleSummaryOther,restPayoutPeriod.getSaleSummaryOther());
        
        if(restPayoutPeriod.hasPayoutFromExternalVendor()){
            this.restPayoutFromExternalVendorList.add(restPayoutPeriod.getRestPayoutFromExternalVendor());
        }

    }
    
    private void updateSaleSummary(RestSaleSummary summary, RestSaleSummary periodSummary){
        summary.setSale(summary.getSale() + periodSummary.getSale());
        summary.setTax(summary.getTax() + periodSummary.getTax());
        summary.setDeliveryFee(summary.getDeliveryFee() + periodSummary.getDeliveryFee());
        summary.setServiceFee(summary.getServiceFee() + periodSummary.getServiceFee());
        summary.setTip(summary.getTip() + periodSummary.getTip());
        summary.setCashSale(summary.getCashSale() + periodSummary.getCashSale());
        summary.setCardSale(summary.getCardSale() + periodSummary.getCardSale());
        summary.setOnlineSale(summary.getOnlineSale() + periodSummary.getOnlineSale());
        summary.setCount(summary.getCount() + periodSummary.getCount());
    }

    private RestPayoutPeriod combinePeriods(RestPayoutPeriod period1, RestPayoutPeriod period2){
        //add all the values from period2 to period 1
        //for the start/end - use period1 start and period2 end
        return period1;
    }

    private void updateAdjustment(){
        restAdjustmentList.clear();
        this.adjustment = 0.0;
        for (RestPayoutPeriod restPayoutPeriod: restPayoutPeriodList) {
            this.adjustment = this.adjustment + restPayoutPeriod.getAdjustment();
            this.restAdjustmentList.addAll(restPayoutPeriod.getRestAdjustmentList());
        }
        //As the adjustment amount may have changed we must also update owingToVendor
        this.owingToVendor = 0.0;
        this.COGS = 0.0;
        for (RestPayoutPeriod restPayoutPeriod: restPayoutPeriodList) {
            this.owingToVendor = this.owingToVendor + restPayoutPeriod.getOwingToVendor();
            this.COGS = this.COGS + restPayoutPeriod.getCOGS();
        }
    }

    /* Determine the date range to use for the specific restaurant
     *  - returns NULL if this restaurant should not be processed this period
     */
    private DateRange findRestPeriodDateRange(Integer startDayOffset, Integer weeksInPeriod, LocalDate rangeStartDate){
        LocalDate restPeriodStart;
        LocalDate restPeriodEnd;

        if(startDayOffset.equals(0)){
            restPeriodEnd = periodEnd;
        }else{
            restPeriodEnd = periodEnd.plusDays(startDayOffset);
        }

        restPeriodStart = restPeriodEnd.minusWeeks(weeksInPeriod).plusDays(1);

        //determine if this range is valid for restaurants processed other than weekly
        if(rangeStartDate==null){
            return new DateRange(restPeriodStart,restPeriodEnd);
        }else{
            Long weeksSinceRangeStart = ChronoUnit.WEEKS.between(rangeStartDate, restPeriodStart);
            boolean isDivisibleByWeeks = weeksSinceRangeStart % weeksInPeriod == 0;
            if(isDivisibleByWeeks){
                //log.info("Process this period: Weeks between:" + rangeStartDate + "and:" + restPeriodStart + " =:" + weeksSinceRangeStart);
                return new DateRange(restPeriodStart,restPeriodEnd);
            }else{
                //log.info("Skip this period: Weeks between:" + rangeStartDate + "and:" + restPeriodStart + " =:" + weeksSinceRangeStart);
                return null;
            }
        }

    }

    public VerticalLayout getMainLayout() {
        return mainLayout;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Double getSale() {
        return Utility.getInstance().round(sale,2);
    }

    public Double getTaxes() {
        return Utility.getInstance().round(taxes,2);
    }

    public Double getTotalSale() {
        return Utility.getInstance().round(totalSale,2);
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public Double getPayoutSale() {
        return Utility.getInstance().round(payoutSale,2);
    }

    public Double getPayoutTaxes() {
        return Utility.getInstance().round(payoutTaxes,2);
    }

    public Double getPayoutTotalSale() {
        return Utility.getInstance().round(payoutTotalSale,2);
    }

    public Integer getPayoutItemCount() {
        return payoutItemCount;
    }

    public Double getPaidSale() {
        return Utility.getInstance().round(paidSale,2);
    }

    public Double getPaidTotalSale() {
        return Utility.getInstance().round(paidTotalSale,2);
    }

    public Integer getPaidItemCount() {
        return paidItemCount;
    }

    public Double getDeliveryFeeFromVendor() {
        return Utility.getInstance().round(deliveryFeeFromVendor,2);
    }

    public Double getDeliveryFeeFromExternal() {
        return Utility.getInstance().round(deliveryFeeFromExternal,2);
    }

    public Double getCommissionForPayout() {
        return Utility.getInstance().round(commissionForPayout,2);
    }

    public Double getCommissionPerDelivery() {
        return Utility.getInstance().round(commissionPerDelivery,2);
    }

    public Double getAdjustment() {
        return Utility.getInstance().round(adjustment,2);
    }

    public Double getOwingToVendor() {
        return Utility.getInstance().round(owingToVendor,2);
    }

    public Double getCOGS(){
        return Utility.getInstance().round(COGS,2);
    }

    public List<RestAdjustment> getRestAdjustmentList() {
        restAdjustmentList.sort(Comparator.comparing(RestAdjustment::getRestaurantName).thenComparing(RestAdjustment::getAdjustmentDate));
        return restAdjustmentList;
    }

    public List<RestPayoutPeriod> getRestPayoutPeriodList() {
        return restPayoutPeriodList;
    }

    public RestPayoutPeriod getPeriod(Long restaurantID){
        if(restPayoutPeriodMap.containsKey(restaurantID)){
            return restPayoutPeriodMap.get(restaurantID).stream().findFirst().orElse(null);
        }
        return null;
    }

    public Double getDirectTotalSale() {
        return Utility.getInstance().round(directTotalSale,2);
    }

    public Double getPhoneInTotalSale() {
        return Utility.getInstance().round(phoneInTotalSale,2);
    }
    public Double getWebOrderTotalSale() {
        return Utility.getInstance().round(webOrderTotalSale,2);
    }
    public Double getWebOrderOnlineTotalSale() {
        return Utility.getInstance().round(webOrderOnlineTotalSale,2);
    }

    public Integer getDirectItemCount() {
        return directItemCount;
    }

    public Integer getPhoneInItemCount() {
        return phoneInItemCount;
    }
    public Integer getWebOrderItemCount() {
        return webOrderItemCount;
    }
    public Integer getWebOrderOnlineItemCount() {
        return webOrderOnlineItemCount;
    }

    public Double getPrePaidTotalSale() {
        return prePaidTotalSale;
    }

    public RestSaleSummary getRestSaleSummary() {
        return restSaleSummary;
    }
    public RestSaleSummary getSaleSummaryPassThru() {
        return saleSummaryPassThru;
    }
    public RestSaleSummary getSaleSummaryOther() {
        return saleSummaryOther;
    }

    public Double getPartMonthCOGS() {
        return Utility.getInstance().round(partMonthCOGS,2);
    }

    public Double getPartMonthTaxes() {
        return Utility.getInstance().round(partMonthTaxes,2);
    }

    @Override
    public String toString() {
        return "RestPayoutSummary{" +
                "periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", sale=" + getSale() +
                ", taxes=" + getTaxes() +
                ", totalSale=" + getTotalSale() +
                ", itemCount=" + itemCount +
                ", payoutSale=" + getPayoutSale() +
                ", payoutTaxes=" + getPayoutTaxes() +
                ", payoutTotalSale=" + getPayoutTotalSale() +
                ", payoutItemCount=" + payoutItemCount +
                ", paidSale=" + getPaidSale() +
                ", paidTotalSale=" + getPaidTotalSale() +
                ", paidItemCount=" + paidItemCount +
                ", deliveryFeeFromVendor=" + getDeliveryFeeFromVendor() +
                ", deliveryFeeFromExternal=" + getDeliveryFeeFromExternal() +
                ", commissionForPayout=" + getCommissionForPayout() +
                ", commissionPerDelivery=" + getCommissionPerDelivery() +
                ", adjustment=" + getAdjustment() +
                ", owingToVendor=" + getOwingToVendor() +
                '}';
    }

    @Override
    public void taskListRefreshNeeded() {
        log.info("refreshNeeded: called: refreshing RestPayoutSummary");
        fullRefresh();;
    }
}
