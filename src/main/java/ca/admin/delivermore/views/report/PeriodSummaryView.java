package ca.admin.delivermore.views.report;

import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.data.intuit.SalesReceipt;
import ca.admin.delivermore.data.report.*;
import ca.admin.delivermore.data.service.intuit.controller.QBOResult;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import com.vaadin.componentfactory.DateRange;
import com.vaadin.componentfactory.EnhancedDateRangePicker;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.olli.FileDownloadWrapper;

import jakarta.annotation.security.RolesAllowed;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@PageTitle("Period Summary")
@Route(value = "periodsummary", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class PeriodSummaryView extends Main implements TaskListRefreshNeededListener {

    private Logger log = LoggerFactory.getLogger(PeriodSummaryView.class);
    private VerticalLayout detailsLayout = new VerticalLayout();
    private EnhancedDateRangePicker rangeDatePicker = new EnhancedDateRangePicker("Select summary period:");
    private Button createSalesReceiptButton = new Button("QBO:Create Sales Receipt");
    private TaskEditDialog taskEditDialog = new TaskEditDialog();

    RestPayoutSummary restPayoutSummary;
    DriverPayoutPeriod driverPayoutPeriod;

    private MissingPOSDataDetails missingPOSDataDetails = new MissingPOSDataDetails();

    LocalDate startDate;
    LocalDate endDate;

    public PeriodSummaryView() {
        configureDatePicker();
        startDate = rangeDatePicker.getValue().getStartDate();
        endDate = rangeDatePicker.getValue().getEndDate();
        buildPeriodDetails();
        add(getToolbar(), getContent());

    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(rangeDatePicker);
        toolbar.setPadding(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");

        //add create sales receipt button
        createSalesReceiptButton.setDisableOnClick(true);
        createSalesReceiptButton.addClickListener(e -> {
            createSalesReceipt();
            createSalesReceiptButton.setEnabled(true);
        });
        toolbar.add(createSalesReceiptButton);

        return toolbar;
    }

    private void createSalesReceipt() {
        SalesReceipt salesReceipt = new SalesReceipt();
        String prefix = "Sales_";
        String prefixMemo = "Period Sales";
        String commonName = "Period Sales";
        salesReceipt.setDocNumber(prefix,endDate);
        salesReceipt.setTxnDate(endDate);
        salesReceipt.setPrivateNote(prefixMemo,startDate,endDate);
        log.info("createSalesReceipt: DocNumber:" + salesReceipt.getDocNumber());
        if(salesReceipt.setCustomerRef(commonName)){
            if(salesReceipt.setPaymentMethodRef(commonName)){
                if(salesReceipt.setDepositToAccountRef(commonName)){
                    //add all the needed lines
                    salesReceipt.addLine(1,restPayoutSummary.getRestSaleSummary().getSale(),"Period Sales:Sale Income","Total Sales Income:Sale Income","Sale Income (+)");
                    salesReceipt.addLine(2,restPayoutSummary.getSaleSummaryPassThru().getDeliveryFee(),"Period Sales:PassThru Delivery Fee Income","Total Sales Income:Fee Income:PassThru Fee Income:Delivery Fee Income","PassThru Delivery Fee Income (+)");
                    salesReceipt.addLine(3,restPayoutSummary.getSaleSummaryPassThru().getServiceFee(),"Period Sales:PassThru Service Fee Income","Total Sales Income:Fee Income:PassThru Fee Income:Service Fee Income","PassThru Service Fee Income (+)");
                    salesReceipt.addLine(4,restPayoutSummary.getSaleSummaryOther().getDeliveryFee(),"Period Sales:Other Delivery Fee Income","Total Sales Income:Fee Income:Other Fee Income:Delivery Fee Income","Other Delivery Fee Income (+)");
                    salesReceipt.addLine(5,restPayoutSummary.getSaleSummaryOther().getServiceFee(),"Period Sales:Other Service Fee Income","Total Sales Income:Fee Income:Other Fee Income:Service Fee Income","Other Service Fee Income (+)");
                    salesReceipt.addLine(6,restPayoutSummary.getRestSaleSummary().getTax(),"Period Sales:Sales tax","Sales Tax Payable","Sales tax (+)");
                    salesReceipt.addLine(7,restPayoutSummary.getRestSaleSummary().getTip(),"Period Sales:Tips payable","Tips payable","Tips payable (+)");
                    salesReceipt.addLine(8,(-1*restPayoutSummary.getRestSaleSummary().getCashSale()),"Period Sales:Cash","Cash on hand","Cash (-)");
                    salesReceipt.addLine(9,(-1*restPayoutSummary.getRestSaleSummary().getCardSale()),"Period Sales:Card Sale","Card/Online Payment Clearing","Card Sale (-)");
                    salesReceipt.addLine(10,(-1*restPayoutSummary.getRestSaleSummary().getOnlineSale()),"Period Sales:Online Sale","Card/Online Payment Clearing","Online Sale (-)");
                }
            }
        }
        if(salesReceipt.getErrorProcessing()){
            log.info("createSalesReceipt: Error creating Sales Receipt DocNumber:" + salesReceipt.getDocNumber());
        }else{
            QBOResult qboResult = salesReceipt.post();
            if(qboResult.getSuccess()){
                log.info("createSalesReceipt: Created Sales Receipt DocNumber:" + salesReceipt.getDocNumber());
                UIUtilities.showNotification("Created Sales Receipt DocNumber:" + salesReceipt.getDocNumber());
            }else{
                log.info("createSalesReceipt: Failed creating Sales Receipt DocNumber:" + salesReceipt.getDocNumber() + " result:" + qboResult.getResult());
                //TODO: need to test show notification error
                UIUtilities.showNotificationError("Failed to created Sales Receipt DocNumber:" + salesReceipt.getDocNumber());
            }
        }

    }

    private Component getContent() {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.add(detailsLayout);
        detailsLayout.setSizeFull();
        return mainLayout;
    }

    private void configureDatePicker() {
        LocalDate defaultDate = LocalDate.parse("2022-08-14");

        //get lastWeek as the default for the range picker
        LocalDate nowDate = LocalDate.now();
        LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate startOfLastWeek = prevSun.minusWeeks(1);
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);

        rangeDatePicker.setPattern("yyyy-MM-dd");
        rangeDatePicker.setParsers("yyyy-MM-dd");

        rangeDatePicker.setMin(defaultDate);
        rangeDatePicker.setValue(new DateRange(startOfLastWeek,endOfLastWeek));
        rangeDatePicker.addValueChangeListener(e -> {
            log.info("configureDatePicker: called: range:" + rangeDatePicker.getValue());
            startDate = rangeDatePicker.getValue().getStartDate();
            endDate = rangeDatePicker.getValue().getEndDate();
            if(startDate!=null){
                createSalesReceiptButton.setEnabled(false);
                buildPeriodDetails();
            }
        });
    }

    private void buildPeriodDetails() {
        detailsLayout.removeAll();
        if(endDate==null) endDate = startDate;

        log.info("buildPeriodDetails: start:" + startDate + " end:" + endDate);

        //build the restaurant sales summary so we can use the totals
        restPayoutSummary = new RestPayoutSummary(startDate,endDate, Boolean.FALSE);

        //build the driver payout for the period summary
        String location = "Strathmore";
        driverPayoutPeriod = new DriverPayoutPeriod(location, startDate, endDate);

        //add a highlevel summary at the top of the income without expenses

        //add each section
        detailsLayout.add(buildTopSummary());
        detailsLayout.add(missingPOSDataDetails.buildMissingPOSData(startDate,endDate));
        detailsLayout.add(buildRestSaleSummary());
        detailsLayout.add(buildDriverPayoutSummary());
        //createSalesReceiptButton.setEnabled(true);

    }

    private VerticalLayout buildTopSummary() {
        VerticalLayout topSummary = UIUtilities.getVerticalLayout();
        HorizontalLayout topSummaryRow = UIUtilities.getHorizontalLayout(false,true,false);
        Double cogsSalesTotal = restPayoutSummary.getCOGS() - restPayoutSummary.getDeliveryFeeFromExternal();

        Double grossProfit = restPayoutSummary.getRestSaleSummary().getFundsTotal() - cogsSalesTotal - driverPayoutPeriod.getDriverCost() - driverPayoutPeriod.getTip() - restPayoutSummary.getPayoutTaxes();
        NumberField grossProfitField = UIUtilities.getNumberField("Gross profit", Utility.getInstance().round(grossProfit,2));

        String fieldWidth = "130px";
        NumberField totalFundsField = UIUtilities.getNumberField("Total Funds",restPayoutSummary.getRestSaleSummary().getFundsTotal());
        totalFundsField.setWidth(fieldWidth);
        NumberField totalSalesCOGSField = UIUtilities.getNumberField("Total Sales COGS",cogsSalesTotal);
        totalSalesCOGSField.setWidth(fieldWidth);
        NumberField driverCostField = UIUtilities.getNumberField("Driver COGS",driverPayoutPeriod.getDriverCost());
        driverCostField.setWidth(fieldWidth);
        NumberField driverTipsField = UIUtilities.getNumberField("Driver Tips",driverPayoutPeriod.getTip());
        driverTipsField.setWidth(fieldWidth);
        //use payoutTaxes as taxes includes places like Smiley's where the taxes have already been paid direct under Wise
        NumberField salesTaxPayableField = UIUtilities.getNumberField("Sales Tax",restPayoutSummary.getPayoutTaxes());
        salesTaxPayableField.setWidth(fieldWidth);

        topSummaryRow.setVerticalComponentAlignment(FlexComponent.Alignment.END);
        topSummaryRow.add(totalFundsField,totalSalesCOGSField,driverCostField,driverTipsField,salesTaxPayableField);

        HorizontalLayout topCOGSSummaryRow = UIUtilities.getHorizontalLayout(false,true,false);
        NumberField totalSalesCOGSSummaryField = UIUtilities.getNumberField("Total Sales COGS",cogsSalesTotal);
        totalSalesCOGSSummaryField.setWidth(fieldWidth);
        NumberField salesCOGSField = UIUtilities.getNumberField("Sales COGS",restPayoutSummary.getCOGS());
        salesCOGSField.setWidth(fieldWidth);
        NumberField cogsReductionDelFeeFromExternalField = UIUtilities.getNumberField("Fee from External",restPayoutSummary.getDeliveryFeeFromExternal());
        cogsReductionDelFeeFromExternalField.setWidth(fieldWidth);
        topCOGSSummaryRow.add(totalSalesCOGSSummaryField,salesCOGSField,cogsReductionDelFeeFromExternalField);

        String label = "Gross Profit before Expenses (" + restPayoutSummary.getRestSaleSummary().getCount() + " sales):";
        topSummary.add(new NativeLabel(label));
        topSummary.add(grossProfitField,topSummaryRow,topCOGSSummaryRow);
        return topSummary;
    }

    private Details buildRestSaleSummary() {
        Details restSaleSummaryDetails = UIUtilities.getDetails();
        //restSaleSummaryDetails.setSizeUndefined();
        VerticalLayout summaryHeader = UIUtilities.getVerticalLayout();
        HorizontalLayout summaryHeaderRow = UIUtilities.getHorizontalLayout(false,true,false);
        VerticalLayout summaryHeaderCol1 = UIUtilities.getVerticalLayout();
        VerticalLayout summaryHeaderCol2 = UIUtilities.getVerticalLayout();
        VerticalLayout summaryHeaderCol3 = UIUtilities.getVerticalLayout();
        VerticalLayout summaryHeaderCol4 = UIUtilities.getVerticalLayout();
        VerticalLayout summaryHeaderCol5 = UIUtilities.getVerticalLayout();

        NativeLabel salesLabel1 = new NativeLabel("Pass Thru");
        NumberField totalSaleField1= UIUtilities.getNumberField("Total Sale",restPayoutSummary.getSaleSummaryPassThru().getSalesTotal());
        NumberField saleField1= UIUtilities.getNumberField("Sale",restPayoutSummary.getSaleSummaryPassThru().getSale());
        NumberField taxField1= UIUtilities.getNumberField("Tax",restPayoutSummary.getSaleSummaryPassThru().getTax());
        NumberField deliveryFeeField1= UIUtilities.getNumberField("Del Fee",restPayoutSummary.getSaleSummaryPassThru().getDeliveryFee());
        NumberField serviceFeeField1= UIUtilities.getNumberField("Srv Fee",restPayoutSummary.getSaleSummaryPassThru().getServiceFee());
        NumberField tipField1= UIUtilities.getNumberField("Tips",restPayoutSummary.getSaleSummaryPassThru().getTip());
        IntegerField countField1 = UIUtilities.getIntegerField("Count", true,restPayoutSummary.getSaleSummaryPassThru().getCount());

        NativeLabel salesLabel2 = new NativeLabel("Other");
        NumberField totalSaleField2= UIUtilities.getNumberField("Total Sale",restPayoutSummary.getSaleSummaryOther().getSalesTotal());
        NumberField saleField2= UIUtilities.getNumberField("Sale",restPayoutSummary.getSaleSummaryOther().getSale());
        NumberField taxField2= UIUtilities.getNumberField("Tax",restPayoutSummary.getSaleSummaryOther().getTax());
        NumberField deliveryFeeField2= UIUtilities.getNumberField("Del Fee",restPayoutSummary.getSaleSummaryOther().getDeliveryFee());
        NumberField serviceFeeField2= UIUtilities.getNumberField("Srv Fee",restPayoutSummary.getSaleSummaryOther().getServiceFee());
        NumberField tipField2= UIUtilities.getNumberField("Tips",restPayoutSummary.getSaleSummaryOther().getTip());
        IntegerField countField2 = UIUtilities.getIntegerField("Count", true,restPayoutSummary.getSaleSummaryOther().getCount());

        NativeLabel salesLabel3 = new NativeLabel("All Sales");
        NumberField totalSaleField3= UIUtilities.getNumberField("Total Sale",restPayoutSummary.getRestSaleSummary().getSalesTotal());
        NumberField saleField3= UIUtilities.getNumberField("Sale",restPayoutSummary.getRestSaleSummary().getSale());
        NumberField taxField3= UIUtilities.getNumberField("Tax",restPayoutSummary.getRestSaleSummary().getTax());
        NumberField deliveryFeeField3= UIUtilities.getNumberField("Del Fee",restPayoutSummary.getRestSaleSummary().getDeliveryFee());
        NumberField serviceFeeField3= UIUtilities.getNumberField("Srv Fee",restPayoutSummary.getRestSaleSummary().getServiceFee());
        NumberField tipField3= UIUtilities.getNumberField("Tips",restPayoutSummary.getRestSaleSummary().getTip());
        IntegerField countField3 = UIUtilities.getIntegerField("Count", true,restPayoutSummary.getRestSaleSummary().getCount());

        NativeLabel salesLabel4 = new NativeLabel("Funds");
        NumberField totalFundsField= UIUtilities.getNumberField("Total Funds",restPayoutSummary.getRestSaleSummary().getFundsTotal());
        NumberField cashSaleField= UIUtilities.getNumberField("Cash Sale",restPayoutSummary.getRestSaleSummary().getCashSale());
        NumberField cardSaleField= UIUtilities.getNumberField("Card Sale",restPayoutSummary.getRestSaleSummary().getCardSale());
        NumberField onlineSaleField= UIUtilities.getNumberField("Online Sale",restPayoutSummary.getRestSaleSummary().getOnlineSale());

        NativeLabel salesLabel5 = new NativeLabel("Other info");
        NumberField salesMinusFundsTotalField = UIUtilities.getNumberField("Sales - Funds",restPayoutSummary.getRestSaleSummary().getSalesMinusFundsTotal());
        NumberField owingToVendorField = UIUtilities.getNumberField("Owing to vendors",restPayoutSummary.getOwingToVendor());
        NumberField cogsField = UIUtilities.getNumberField("COGS",restPayoutSummary.getCOGS());
        summaryHeaderCol1.add(salesLabel1,totalSaleField1,saleField1,taxField1,deliveryFeeField1,serviceFeeField1,tipField1,countField1);
        summaryHeaderCol2.add(salesLabel2,totalSaleField2,saleField2,taxField2,deliveryFeeField2,serviceFeeField2,tipField2,countField2);
        summaryHeaderCol3.add(salesLabel3,totalSaleField3,saleField3,taxField3,deliveryFeeField3,serviceFeeField3,tipField3,countField3);
        summaryHeaderCol4.add(salesLabel4,totalFundsField,cashSaleField,cardSaleField,onlineSaleField);
        summaryHeaderCol5.add(salesLabel5,salesMinusFundsTotalField,owingToVendorField,cogsField);
        String label = "Sales Summary (" + restPayoutSummary.getRestSaleSummary().getCount() + " sales):";
        summaryHeader.add(new NativeLabel(label),summaryHeaderRow);
        summaryHeaderRow.add(summaryHeaderCol1,summaryHeaderCol2,summaryHeaderCol3,summaryHeaderCol4,summaryHeaderCol5);
        restSaleSummaryDetails.setSummary(summaryHeader);
        if(!restPayoutSummary.getRestSaleSummary().getSalesMinusFundsTotal().equals(0.0)){
            restSaleSummaryDetails.setOpened(true);
            createSalesReceiptButton.setEnabled(false);
        }else{
            restSaleSummaryDetails.setOpened(false);
            createSalesReceiptButton.setEnabled(true);
        }
        Grid<RestPayoutPeriod> grid = new Grid<>();
        restSaleSummaryDetails.add(grid);
        grid.setItems(restPayoutSummary.getRestPayoutPeriodList());
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setColumnReorderingAllowed(true);
        grid.addComponentColumn(item -> {
            Icon editIcon = new Icon("vaadin", "file-text");
            editIcon.setColor(UIUtilities.iconColorNormal);
            editIcon.setTooltipText("Vendor Report (pdf)");
            editIcon.setSize("16px");
            FileDownloadWrapper downloadWrapper = new FileDownloadWrapper(
                    new StreamResource(item.getPdfFileName(), () -> {
                        try {
                            return new ByteArrayInputStream(Files.readAllBytes( item.getPdfFile().toPath()));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    })
            );

            downloadWrapper.wrapComponent(editIcon);

            editIcon.addClickListener(e -> {
                File appPath = new File(System.getProperty("user.dir"));
                File outputDir = new File(appPath,"tozip");
                Utility.emptyDir(outputDir);
                try {
                    Files.createDirectory(outputDir.toPath());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                item.createStatement();
            });
            return downloadWrapper;
        }).setWidth("50px").setFlexGrow(0).setFrozen(true);
        grid.addColumn(RestPayoutPeriod::getRestaurantName).setHeader("Restaurant").setFrozen(true);
        grid.addColumn(RestPayoutPeriod::getPeriodRange).setHeader("Period");
        grid.addColumn(item -> item.getRestSaleSummary().getSalesTotal()).setHeader("Sale Total");
        grid.addColumn(item -> item.getRestSaleSummary().getFundsTotal()).setHeader("Funds Total");
        grid.addColumn(item -> item.getRestSaleSummary().getSalesMinusFundsTotal()).setHeader("Diff");
        grid.addColumn(item -> item.getRestSaleSummary().getCount()).setHeader("Count");
        grid.addColumn(item -> item.getRestSaleSummary().getSale()).setHeader("Sale");
        grid.addColumn(item -> item.getRestSaleSummary().getTax()).setHeader("Tax");
        grid.addColumn(item -> item.getRestSaleSummary().getDeliveryFee()).setHeader("Del Fee");
        grid.addColumn(item -> item.getSaleSummaryPassThru().getDeliveryFee()).setHeader("PassThru Del Fee");
        grid.addColumn(item -> item.getSaleSummaryOther().getDeliveryFee()).setHeader("Other Del Fee");
        grid.addColumn(item -> item.getRestSaleSummary().getServiceFee()).setHeader("Srv Fee");
        grid.addColumn(item -> item.getSaleSummaryPassThru().getServiceFee()).setHeader("PassThru Srv Fee");
        grid.addColumn(item -> item.getSaleSummaryOther().getServiceFee()).setHeader("Other Srv Fee");
        grid.addColumn(item -> item.getRestSaleSummary().getTip()).setHeader("Tip");
        grid.addColumn(item -> item.getRestSaleSummary().getCashSale()).setHeader("Cash Sale");
        grid.addColumn(item -> item.getRestSaleSummary().getCardSale()).setHeader("Card Sale");
        grid.addColumn(item -> item.getRestSaleSummary().getOnlineSale()).setHeader("Online Sale");
        grid.addColumn(RestPayoutPeriod::getOwingToVendor).setHeader("Owing to Vendor");
        grid.addColumn(RestPayoutPeriod::getPaidToVendor).setHeader("Paid to Vendor");
        grid.addColumn(RestPayoutPeriod::getCOGS).setHeader("COGS");

        //TODO: add a detail renderer
        grid.setItemDetailsRenderer(new ComponentRenderer<>(item -> {
            VerticalLayout div = new VerticalLayout();
            List<RestSaleSummary> list = item.getRestSaleSummaryList();
            log.info("ItemDetailsRenderer: size" + list.size());

            Grid<RestSaleSummary> details = new Grid<>();
            //details.setSizeFull();
            //details.addThemeNames("no-row-borders", "row-stripes", "no-headers");
            details.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
            details.addThemeVariants(GridVariant.LUMO_COMPACT);
            if (!list.isEmpty()) {
                details.setItems(list);
                //details.setDataProvider(new ListDataProvider<>(list));
                //details.setHeightByRows(true);
                details.setWidth("900px");
                details.setMaxHeight("400px");
                details.setMinHeight("200px");
                div.setSizeFull();
                div.add(details);
            }
            details.addComponentColumn(detailItem -> {
                Icon editIcon = new Icon("lumo", "edit");
                editIcon.setTooltipText("Edit/View Task");
                editIcon.addClickListener(e -> {
                    taskEditDialog.setDialogMode(TaskEditDialog.DialogMode.EDIT);
                    taskEditDialog.dialogOpen(detailItem.getJobId());
                });
                return editIcon;
            }).setWidth("50px").setFlexGrow(0);

            details.addColumn(new LocalDateTimeRenderer<>(RestSaleSummary::getDateTime,"MM-dd HH:mm")).setHeader("Created");
            details.addColumn(RestSaleSummary::getSalesTotal).setHeader("Sales Total");
            details.addColumn(RestSaleSummary::getFundsTotal).setHeader("Funds Total");
            details.addColumn(RestSaleSummary::getSalesMinusFundsTotal).setHeader("Dif");
            details.addColumn(RestSaleSummary::getSale).setHeader("Sale");
            details.addColumn(RestSaleSummary::getTax).setHeader("Tax");
            details.addColumn(RestSaleSummary::getDeliveryFee).setHeader("Del Fee");
            details.addColumn(RestSaleSummary::getServiceFee).setHeader("Srv Fee");
            return div;
        }));

        return restSaleSummaryDetails;
    }

    private Details buildDriverPayoutSummary() {
        Details driverPayoutSummaryDetails = UIUtilities.getDetails();
        //driverPayoutSummaryDetails.setSizeUndefined();
        HorizontalLayout summaryHeader = UIUtilities.getHorizontalLayout();
        NumberField driverCostField = UIUtilities.getNumberField("Cost",driverPayoutPeriod.getDriverCost());
        NumberField driverPayField = UIUtilities.getNumberField("Pay",driverPayoutPeriod.getDriverPay());
        NumberField driverAdjField = UIUtilities.getNumberField("Adj",driverPayoutPeriod.getDriverAdjustment());
        NumberField driverCashField = UIUtilities.getNumberField("Cash",driverPayoutPeriod.getDriverCash());
        NumberField driverTipsfield = UIUtilities.getNumberField("Tips",driverPayoutPeriod.getTip());
        NumberField driverPayoutField = UIUtilities.getNumberField("Payout",driverPayoutPeriod.getDriverPayout());
        summaryHeader.add(new NativeLabel("Driver Cost:"),driverCostField,driverPayField,driverAdjField,driverCashField,driverTipsfield,driverPayoutField);
        driverPayoutSummaryDetails.setSummary(summaryHeader);
        Grid<DriverPayoutWeek> grid = new Grid<>();
        driverPayoutSummaryDetails.add(grid);
        grid.setItems(driverPayoutPeriod.getDriverPayoutWeekList());
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setColumnReorderingAllowed(true);
        grid.addColumn(DriverPayoutWeek::getFleetName).setHeader("Driver");
        grid.addColumn(DriverPayoutWeek::getDriverCostFmt).setHeader("COGS").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(DriverPayoutWeek::getDriverPayFmt).setHeader("Pay").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(DriverPayoutWeek::getDriverAdjustmentFmt).setHeader("Adjustment").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(DriverPayoutWeek::getDriverCashFmt).setHeader("Cash").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(DriverPayoutWeek::getTipFmt).setHeader("Tips").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(DriverPayoutWeek::getDriverPayoutFmt).setHeader("Payout").setTextAlign(ColumnTextAlign.END);
        return driverPayoutSummaryDetails;

    }


    @Override
    public void taskListRefreshNeeded() {
        buildPeriodDetails();
    }
}
