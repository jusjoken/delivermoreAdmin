package ca.admin.delivermore.data.report;

import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.data.entity.RestAdjustment;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.RestAdjustmentRepository;
import ca.admin.delivermore.views.UIUtilities;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.data.renderer.NumberRenderer;

import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.ConverterTypeVia;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.core.XDocReportException;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.theme.lumo.LumoIcon;

public class RestPayoutPeriod implements Serializable {

    private Logger log = LoggerFactory.getLogger(RestPayoutPeriod.class);
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long restaurantId;
    private String restaurantName;
    private String restaurantEmail;
    private Boolean hasPayout = Boolean.TRUE;
    private Double sale = 0.0;
    private Double taxes = 0.0;
    private Double totalSale = 0.0;
    private Integer itemCount = 0;
    private Double paidToVendor = 0.0;

    private Double payoutSale = 0.0;
    private Double payoutTaxes = 0.0;
    private Double payoutTotalSale = 0.0;
    private Integer payoutItemCount = 0;

    private Double paidSale = 0.0;
    private Double paidTotalSale = 0.0;
    private Integer paidItemCount = 0;
    private Double directSale = 0.0;
    private Double directTotalSale = 0.0;
    private Double phoneInTotalSale = 0.0;
    private Double webOrderTotalSale = 0.0;
    private Double webOrderOnlineTotalSale = 0.0;

    private Double prePaidTotalSale = 0.0;

    private Double deliveryFeeFromVendor = 0.0;
    private Double deliveryFeeFromExternal = 0.0;
    private Integer deliveryFeeFromExternalCount = 0;
    private String deliveryFeeFromExternalVendorName = null;
    private Double commissionForPayout = 0.0;
    private Double commissionPerDelivery = 0.0;
    private Double commissionRate = 0.0;
    private Double commissionRatePhonein = 0.0;
    private Double adjustment = 0.0;
    private Double owingToVendor = 0.0;
    private Restaurant restaurant;
    private List<TaskEntity> taskEntityList = new ArrayList<>();
    private List<RestSaleSummary> restSaleSummaryList = new ArrayList<>();
    private List<RestPayoutItem> paidRestItems = new ArrayList<>();
    private List<RestPayoutItem> payoutRestItems = new ArrayList<>();
    private List<RestPayoutItem> cancelledRestItems = new ArrayList<>();
    private List<RestAdjustment> restAdjustmentList = new ArrayList<>();
    private List<RestPayoutItem> saleItemsDirect = new ArrayList<>();
    private List<RestPayoutItem> saleItemsPhoneIn = new ArrayList<>();
    private List<RestPayoutItem> saleItemsWebOrderOnline = new ArrayList<>();
    private List<RestPayoutItem> saleItemsWebOrder = new ArrayList<>();

    private Details periodDetails;
    private VerticalLayout mainLayout = new VerticalLayout();

    private RestAdjustmentRepository restAdjustmentRepository;
    private RestPayoutAdjustmentDialog adjustmentDialog;
    private RestPayoutSummary restPayoutSummary;
    private File appPath = new File(System.getProperty("user.dir"));
    private File outputDir = new File(appPath,"tozip");
    private File pdfFile = null;
    private String pdfFileName = "";
    @Value("classpath:Rest_PayStatement_Template.docx")
    private Resource resourcePayStatementTemplate;

    private RestSaleSummary restSaleSummary = new RestSaleSummary();
    private RestSaleSummary saleSummaryPassThru = new RestSaleSummary();
    private RestSaleSummary saleSummaryOther = new RestSaleSummary();

    public RestPayoutPeriod(LocalDate periodStart, LocalDate periodEnd, Restaurant restaurant, RestPayoutSummary restPayoutSummary) {
        this.restPayoutSummary = restPayoutSummary;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.restaurant = restaurant;
        this.resourcePayStatementTemplate = new ClassPathResource("Rest_PayStatement_Template.docx");
        restAdjustmentRepository = Registry.getBean(RestAdjustmentRepository.class);
        adjustmentDialog = new RestPayoutAdjustmentDialog(periodStart);
        this.pdfFileName = "VendorSalesStatement-" + this.restaurant.getName() + periodStart + "-" + periodEnd + ".pdf";
        buildPayoutPeriod();
        refresh();
    }

    public void refresh(){
        updateAdjustment();
        buildPayoutPeriodLayout();
    }

    public void openDetails(){
        periodDetails.setOpened(true);
    }

    private void buildPayoutPeriodLayout() {
        mainLayout.removeAll();
        periodDetails = UIUtilities.getDetails();
        //periodDetails.setSizeUndefined();
        mainLayout.add(periodDetails);
        VerticalLayout periodDetailsSummary = UIUtilities.getVerticalLayout();
        HorizontalLayout periodDetailsSummaryHeader = UIUtilities.getHorizontalLayout();
        HorizontalLayout periodDetailsSummaryOwing = UIUtilities.getHorizontalLayoutNoWidthCentered();
        HorizontalLayout periodDetailsSummaryFields = UIUtilities.getHorizontalLayout();
        VerticalLayout periodDetailsContent = UIUtilities.getVerticalLayout();
        String summaryTitle = restaurantName + ": " + periodStart + " - " + periodEnd;
        NumberField periodOwing = UIUtilities.getNumberField("", getOwingToVendor());
        NativeLabel periodOwingLabel = new NativeLabel("Owing :");
        periodDetailsSummaryOwing.add(periodOwingLabel,periodOwing);
        periodDetailsSummaryHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        periodDetailsSummaryHeader.setAlignItems(FlexComponent.Alignment.START);
        periodDetailsSummaryHeader.add(new Text(summaryTitle),periodDetailsSummaryOwing);
        periodDetailsSummary.add(periodDetailsSummaryHeader,periodDetailsSummaryFields);
        periodDetails.setSummary(periodDetailsSummary);
        periodDetails.add(periodDetailsContent);
        NumberField periodPayoutSale = UIUtilities.getNumberField("Sales", getPayoutSale());
        NumberField periodPayoutTaxes = UIUtilities.getNumberField("Taxes", getPayoutTaxes());
        NumberField periodPayoutTotalSale = UIUtilities.getNumberField("TotalSales", getPayoutTotalSale());
        TextField periodItemCount = UIUtilities.getTextFieldRO("Count", getPayoutItemCount().toString(),"100px");
        NumberField periodPayoutCOGS = UIUtilities.getNumberField("COGS", getCOGS());
        NumberField periodDeliveryFeeFromVendor = UIUtilities.getNumberField("Fee from Vendor",getDeliveryFeeFromVendor());
        NumberField periodCommission = UIUtilities.getNumberField("Commission", getCommissionForPayout());
        NumberField periodPrePaidSalesOrCommissionPer;
        if(getPrePaidTotalSale()>0){
            periodPrePaidSalesOrCommissionPer = UIUtilities.getNumberField("PrePaid Sales", getPrePaidTotalSale());
        }else{
            periodPrePaidSalesOrCommissionPer = UIUtilities.getNumberField("Commission Per", getCommissionPerDelivery());
        }
        NumberField periodAdjustment = UIUtilities.getNumberField("Adjustment", getAdjustment());
        periodDetailsSummaryFields.add(
                periodPayoutSale,
                periodPayoutTaxes,
                periodPayoutTotalSale,
                periodItemCount,
                periodPayoutCOGS,
                periodDeliveryFeeFromVendor,
                periodCommission,
                periodPrePaidSalesOrCommissionPer,
                periodAdjustment
        );
        if(hasPayoutFromExternalVendor()){
            periodDetailsSummary.add(getExternalVendorItem(getRestPayoutFromExternalVendor()));
        }
        //create content area for restaurant
        //Grid for payout items
        Integer labelCount = getPayoutItemCount();
        String labelString = "Payout Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getPayoutTotalSale());
        periodDetailsContent.add(getItemGrid(labelString, getPayoutRestItems(), Boolean.TRUE, Boolean.FALSE));
        //Grid for adjustments
        periodDetailsContent.add((getAdjustmentsGrid()));
        //Grid for cancelled items
        labelCount = getCancelledRestItems().size();
        labelString = "Cancelled Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items");
        periodDetailsContent.add(getItemGrid(labelString, getCancelledRestItems(), Boolean.FALSE, Boolean.FALSE));
        //Include Total Sales if there are both phone-in and direct sales
        if(hasNonDirectSales()){
            labelCount = getItemCount();
            //Double totalSaleIncludingWebOrderOnline = getTotalSale() + getPrePaidTotalSale();
            labelString = "Total Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getTotalSale());
            NativeLabel totalSalesLabel = new NativeLabel(labelString);
            periodDetailsContent.add(totalSalesLabel);
        }
        //Details/Grid for direct sales items
        labelCount = getDirectSalesCount();
        labelString = "Direct Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getDirectTotalSale());
        periodDetailsContent.add(getSalesBySaleType(labelString, getDirectSalesCount(), directTotalSale, getSaleItemsDirect(), RestPayoutItem.SaleType.DIRECT));
        //Details/Grid for phonein sales items
        if(getPhoneInSalesCount()>0){
            labelCount = getPhoneInSalesCount();
            labelString = "Phone-In Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getPhoneInTotalSale());
            periodDetailsContent.add(getSalesBySaleType(labelString, getPhoneInSalesCount(), phoneInTotalSale, getSaleItemsPhoneIn(), RestPayoutItem.SaleType.PHONEIN));
        }
        if(getWebOrderSalesCount()>0){
            labelCount = getWebOrderSalesCount();
            labelString = "Web Order Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getWebOrderTotalSale());
            periodDetailsContent.add(getSalesBySaleType(labelString, getWebOrderSalesCount(), webOrderTotalSale, getSaleItemsWebOrder(), RestPayoutItem.SaleType.WEBORDER));
        }
        if(getWebOrderOnlineSalesCount()>0){
            labelCount = getWebOrderOnlineSalesCount();
            labelString = "Web Order Online Sales (" + labelCount + " " + UIUtilities.singlePlural(labelCount, "item", "items") + ") Total Sales " + UIUtilities.getNumberFormatted(getWebOrderOnlineTotalSale());
            periodDetailsContent.add(getSalesBySaleType(labelString, getWebOrderOnlineSalesCount(), webOrderOnlineTotalSale, getSaleItemsWebOrderOnline(), RestPayoutItem.SaleType.WEBORDERONLINE));
        }
    }

    private Details getSalesBySaleType(String label, Integer count, Double sales, List<RestPayoutItem> salesList, RestPayoutItem.SaleType saleType ) {
        Details salesDetails = UIUtilities.getDetails();
        salesDetails.setSummaryText(label);
        Boolean includeGlobalFields = Boolean.TRUE;
        if(!saleType.equals(RestPayoutItem.SaleType.DIRECT)) includeGlobalFields = Boolean.FALSE;
        Boolean showPrePaidSaleColumn = Boolean.FALSE;
        if(saleType.equals(RestPayoutItem.SaleType.WEBORDERONLINE)){
            showPrePaidSaleColumn = Boolean.TRUE;
            includeGlobalFields = Boolean.FALSE;
        }
        VerticalLayout salesLayout = getItemGrid(null, salesList, includeGlobalFields, showPrePaidSaleColumn);
        salesDetails.add(salesLayout);
        return salesDetails;
    }

    private VerticalLayout getAdjustmentsGrid(){
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout(true,true,false);
        String labelString = "Adjustments (" + restAdjustmentList.size() + " " + UIUtilities.singlePlural(restAdjustmentList.size(), "item", "items") + ")";
        NativeLabel gridLabel = new NativeLabel(labelString);

        HorizontalLayout periodAdustmentsToolbar = UIUtilities.getHorizontalLayout(true,true,false);
        Icon addNewIcon = LumoIcon.PLUS.create();
        addNewIcon.setColor("green");
        Button adjustmentsAddNew = new Button("Add", addNewIcon);

        adjustmentsAddNew.addThemeVariants(ButtonVariant.LUMO_SMALL);
        adjustmentsAddNew.addClickListener(e -> {
            adjustmentDialog.setDialogMode(RestPayoutAdjustmentDialog.DialogMode.NEW_FIXED_REST);
            adjustmentDialog.dialogOpen(new RestAdjustment(),this.restPayoutSummary, this);
        });
        periodAdustmentsToolbar.add(gridLabel,adjustmentsAddNew);
        gridLayout.add(periodAdustmentsToolbar);
        if(restAdjustmentList.size()>0){

            Grid<RestAdjustment> grid = new Grid<>();
            grid.setItems(restAdjustmentList);
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
                    adjustmentDialog.dialogOpen(item,this.restPayoutSummary, this);
                });
                return editIcon;
            }).setWidth("150px").setFlexGrow(0);
            grid.addComponentColumn(item -> {
                Icon deleteIcon = LumoIcon.CROSS.create();
                deleteIcon.setColor("red");
                deleteIcon.addClickListener(e -> {
                    adjustmentDialog.setDialogMode(RestPayoutAdjustmentDialog.DialogMode.DELETE);
                    adjustmentDialog.dialogOpen(item,this.restPayoutSummary, this);
                });
                return deleteIcon;
            }).setWidth("150px").setFlexGrow(0);
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
            gridLayout.add(grid);
        }
        return gridLayout;
    }

    private VerticalLayout getItemGrid(String label, List<RestPayoutItem> list, Boolean includeGlobalFields, Boolean showPrePaidSaleColumn){
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout(true,true,false);
        if(list.size()>0){
            Grid<RestPayoutItem> grid = new Grid<>();
            if(label==null){
                gridLayout.add(grid);
            }else{
                NativeLabel gridLabel = new NativeLabel(label);
                gridLayout.add(gridLabel,grid);
            }
            grid.setItems(list);
            grid.setWidthFull();
            grid.setAllRowsVisible(true);
            grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
            grid.addThemeVariants(GridVariant.LUMO_COMPACT);
            String numberColWidth = "100px";
            grid.addColumn(new LocalDateTimeRenderer<>(RestPayoutItem::getCreationDateTime,"MM/dd HH:mm"))
                    .setSortable(true)
                    .setHeader("Date/Time");
            if(includeGlobalFields){
                grid.addColumn(item -> checkValueForNull(item.getOrderId()))
                        .setComparator(RestPayoutItem::getOrderId)
                        .setSortable(true)
                        .setHeader("Order");
                grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getSale()))
                        .setComparator(RestPayoutItem::getSale)
                        .setSortable(true)
                        .setHeader("Sale").setTextAlign(ColumnTextAlign.END);
                grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getTaxes()))
                        .setComparator(RestPayoutItem::getTaxes)
                        .setHeader("Tax").setTextAlign(ColumnTextAlign.END);
            }
            if(showPrePaidSaleColumn){
                grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getPrePaidTotalSale()))
                        .setComparator(RestPayoutItem::getTotalSale)
                        .setSortable(true)
                        .setHeader("Online TotalSale").setTextAlign(ColumnTextAlign.END);
            }else{
                grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getTotalSale()))
                        .setComparator(RestPayoutItem::getTotalSale)
                        .setSortable(true)
                        .setHeader("TotalSale").setTextAlign(ColumnTextAlign.END);
                grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDeliveryFee()))
                        .setComparator(RestPayoutItem::getDeliveryFee)
                        .setSortable(true)
                        .setHeader("Delivery Fee").setTextAlign(ColumnTextAlign.END);
            }
            grid.addColumn(item -> UIUtilities.getNumberFormatted(item.getDeliveryFeeFromVendor()))
                    .setComparator(RestPayoutItem::getDeliveryFeeFromVendor)
                    .setHeader("FeeFromVendor").setTextAlign(ColumnTextAlign.END);
            grid.addColumn(RestPayoutItem::getPaymentMethod)
                    .setSortable(true)
                    .setHeader("Method");
            grid.getColumns().forEach(col -> col.setAutoWidth(true));
        }
        return gridLayout;
    }

    private String checkValueForNull(Long value){
        if(value==null) return "";
        return value.toString();
    }

    private void buildPayoutPeriod() {
        this.restaurantId = restaurant.getRestaurantId();
        this.restaurantName = restaurant.getName();
        this.restaurantEmail = restaurant.getEmail();
        this.commissionRate = restaurant.getCommissionRate();
        this.commissionRatePhonein = restaurant.getCommissionRatePhonein();

        if(restaurant.getPosGlobal() && restaurant.getPosPhonein()){
            this.hasPayout = Boolean.FALSE;
        }

        this.deliveryFeeFromExternalVendorName = restaurant.getDeliveryFeeFromExternalVendorName();
        TaskDetailRepository taskDetailRepository = Registry.getBean(TaskDetailRepository.class);
        taskEntityList = taskDetailRepository.getTaskEntityByDateAndRestaurant(periodStart.atStartOfDay(), periodEnd.atTime(23,59,59), restaurant.getRestaurantId());
        this.itemCount = taskEntityList.size();

        for (TaskEntity taskEntity: taskEntityList) {
            RestPayoutItem restPayoutItem = new RestPayoutItem(taskEntity);

            //handle situation where external vendor like Opa Corporate needs to be invoiced per FREE delivery
            if(restaurant.getDeliveryFeeFromExternal()>0.0){
                if(taskEntity.getDeliveryFee().equals(0.0)){
                    this.deliveryFeeFromExternal = this.deliveryFeeFromExternal + restaurant.getDeliveryFeeFromExternal();
                    this.deliveryFeeFromExternalCount++;
                }
            }
            if(restaurant.getCommissionPerDelivery()>0.0){
                this.commissionPerDelivery = this.commissionPerDelivery + restaurant.getCommissionPerDelivery();
            }

            //add in the delivery fee from vendor for Web Orders
            if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDER) || restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDERONLINE)){
                restPayoutItem.setDeliveryFeeFromVendor(restaurant.getDeliveryFeeFromVendorWebOrder());
            }else{
                //add in the deliveryFeeFromVendor for free deliveries
                if(restaurant.getDeliveryFeeFromVendor()>0.0){
                    //only free deliveries
                    if(taskEntity.getDeliveryFee().equals(0.0)){
                        restPayoutItem.setDeliveryFeeFromVendor(restaurant.getDeliveryFeeFromVendor());
                    }
                }
            }


            Boolean posPayment;
            if(taskEntity.getCreatedBy().equals(43L)){  //Global
                posPayment = restaurant.getPosGlobal();
            }else{
                posPayment = restaurant.getPosPhonein();
            }

            //add commissionPerPhoneIn for restaurants like Mike's
            if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.PHONEIN)){
                if(restaurant.getCommissionPerPhonein()>0.0){
                    this.commissionPerDelivery = this.commissionPerDelivery + restaurant.getCommissionPerPhonein();
                }
            }

            if(posPayment){
                paidRestItems.add(restPayoutItem);
                this.paidSale = this.paidSale + restPayoutItem.getSale();
                this.paidTotalSale = this.paidTotalSale + restPayoutItem.getTotalSale();
            }else{
                payoutRestItems.add(restPayoutItem);
                //handle web order online differently as the restaurant already collected the funds
                if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDERONLINE)){
                    //do not add the taxes/totalsale as vendor already collected these
                    this.payoutSale = this.payoutSale + restPayoutItem.getSale();
                    this.payoutTaxes = this.payoutTaxes + restPayoutItem.getTaxes();
                    this.payoutTotalSale = this.payoutTotalSale + restPayoutItem.getTotalSale();
                    this.deliveryFeeFromVendor = this.deliveryFeeFromVendor + restPayoutItem.getDeliveryFeeFromVendor();
                    this.prePaidTotalSale = this.prePaidTotalSale + restPayoutItem.getPrePaidTotalSale();
                }else{
                    this.payoutSale = this.payoutSale + restPayoutItem.getSale();
                    this.payoutTaxes = this.payoutTaxes + restPayoutItem.getTaxes();
                    this.payoutTotalSale = this.payoutTotalSale + restPayoutItem.getTotalSale();
                    this.deliveryFeeFromVendor = this.deliveryFeeFromVendor + restPayoutItem.getDeliveryFeeFromVendor();
                }
            }
            this.sale = this.sale + restPayoutItem.getSale();
            this.taxes = this.taxes + restPayoutItem.getTaxes();
            this.totalSale = this.totalSale + restPayoutItem.getTotalSale();

            if(this.restaurantId.equals(0L)){  //custom paidToVendor is the Sale price
                this.paidToVendor = this.paidToVendor + restPayoutItem.getSale();
            }else{
                this.paidToVendor = this.paidToVendor + restPayoutItem.getPaidToVendor();
            }

            if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.DIRECT)){
                saleItemsDirect.add(restPayoutItem);
                this.directSale = this.directSale + restPayoutItem.getSale();
                this.directTotalSale = this.directTotalSale + restPayoutItem.getTotalSale();
            }else if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDER)){
                saleItemsWebOrder.add(restPayoutItem);
                this.webOrderTotalSale = this.webOrderTotalSale + restPayoutItem.getTotalSale();
            }else if(restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDERONLINE)){
                saleItemsWebOrderOnline.add(restPayoutItem);
                this.webOrderOnlineTotalSale = this.webOrderOnlineTotalSale + restPayoutItem.getPrePaidTotalSale();
            }else{
                saleItemsPhoneIn.add(restPayoutItem);
                this.phoneInTotalSale = this.phoneInTotalSale + restPayoutItem.getTotalSale();
            }

            //Create a salesRecord for all orders
            //create a salesRecord - DO NOT INCLUDE WebOrderOnline as we do not collect for those sales
            if(!restPayoutItem.getSaleType().equals(RestPayoutItem.SaleType.WEBORDERONLINE)){
                restSaleSummaryList.add(createSaleSummary(restPayoutItem,taskEntity));
                updateSaleSummary(restSaleSummary,restPayoutItem,taskEntity);
                //create a salesRecords to separate sales where Tax is passed thru to vendor and other where tax is due
                if(taskEntity.getCreatedBy().equals(43L) && !taskEntity.getPosPayment()){
                    updateSaleSummary(saleSummaryPassThru,restPayoutItem,taskEntity);
                }else{
                    updateSaleSummary(saleSummaryOther,restPayoutItem,taskEntity);
                }
            }else{
                restSaleSummary.setCount(restSaleSummary.getCount() + 1);
                saleSummaryOther.setCount(saleSummaryOther.getCount() + 1);
            }
        }
        this.paidItemCount = paidRestItems.size();
        this.payoutItemCount = payoutRestItems.size();

        //this.commissionForPayout = Utility.getInstance().round(this.payoutSale * rateInt / 100,2);
        //log.info("***TESTING RATE**** payoutSale:" + this.payoutSale + "rateInt:" + rateInt + " as int:" + this.commissionForPayout + " as double:" + Utility.getInstance().round(this.payoutSale * this.commissionRate,2));
        // if commission rate is different for phone-in vs direct then
        //  - need to calculate commission on directSales - sales NOT directTotalSale
        //  - and phoneInTotalSales
        //  - and add them together
        Double commissionDirect = this.directSale * this.commissionRate;
        if(restaurant.getPosPhonein()){
            this.commissionForPayout = Utility.getInstance().round(commissionDirect,2);
        }else{
            Double commissionPhoneIn = this.phoneInTotalSale * this.commissionRatePhonein;
            Double commissionWebOrder = this.webOrderTotalSale * this.commissionRatePhonein;
            Double commissionWebOrderOnline = this.webOrderOnlineTotalSale * this.commissionRatePhonein;
            Double commissionOther = commissionPhoneIn + commissionWebOrder + commissionWebOrderOnline;
            this.commissionForPayout = Utility.getInstance().round(commissionDirect + commissionOther,2);
        }

        List<TaskEntity> cancelledTaskEntityList = taskDetailRepository.getTaskEntityByDateAndRestaurantCancelled(periodStart.atStartOfDay(), periodEnd.atTime(23,59,59), restaurant.getRestaurantId());
        for (TaskEntity taskEntity: cancelledTaskEntityList) {
            RestPayoutItem restPayoutItem = new RestPayoutItem(taskEntity);
            restPayoutItem.setItemType(RestPayoutItem.ItemType.CANCELLED);
            cancelledRestItems.add(restPayoutItem);
        }
    }

    private void updateSaleSummary(RestSaleSummary summary, RestPayoutItem restPayoutItem, TaskEntity taskEntity){
        summary.setCount(summary.getCount() + 1);
        summary.setSale(summary.getSale() + restPayoutItem.getSale());
        summary.setTax(summary.getTax() + restPayoutItem.getTaxes());
        summary.setDeliveryFee(summary.getDeliveryFee() + taskEntity.getDeliveryFee());
        summary.setServiceFee(summary.getServiceFee() + taskEntity.getServiceFee());
        summary.setTip(summary.getTip() + taskEntity.getTip());

        //totalSale is calculated on Global in TaskEntity from Global data and for others is the totalWithFees
        // - neither include the tip so add it in here
        Double totalSale = 0.0;
        totalSale = taskEntity.getTotalSale() + taskEntity.getTip();

        if(taskEntity.getPaymentMethod().equalsIgnoreCase("CASH")){
            summary.setCashSale(summary.getCashSale() + totalSale);
        }else if(taskEntity.getPaymentMethod().equalsIgnoreCase("CARD")){
            summary.setCardSale(summary.getCardSale() + totalSale);
        }else{
            summary.setOnlineSale(summary.getOnlineSale() + totalSale);
        }
    }

    private RestSaleSummary createSaleSummary(RestPayoutItem restPayoutItem, TaskEntity taskEntity){
        RestSaleSummary summary = new RestSaleSummary();
        summary.setCount(1);
        summary.setDateTime(taskEntity.getCreationDate());
        summary.setJobId(taskEntity.getJobId());
        summary.setSale(restPayoutItem.getSale());
        summary.setTax(restPayoutItem.getTaxes());
        summary.setDeliveryFee(taskEntity.getDeliveryFee());
        summary.setServiceFee(taskEntity.getServiceFee());
        summary.setTip(taskEntity.getTip());

        //totalSale is calculated on Global in TaskEntity from Global data and for others is the totalWithFees
        // - neither include the tip so add it in here
        Double totalSale = 0.0;
        totalSale = taskEntity.getTotalSale() + taskEntity.getTip();

        if(taskEntity.getPaymentMethod().equalsIgnoreCase("CASH")){
            summary.setCashSale(totalSale);
        }else if(taskEntity.getPaymentMethod().equalsIgnoreCase("CARD")){
            summary.setCardSale(totalSale);
        }else{
            summary.setOnlineSale(totalSale);
        }
        return summary;
    }

    private void updateAdjustment(){
        restAdjustmentList.clear();
        restAdjustmentList = restAdjustmentRepository.findByRestaurantIdAndAdjustmentDateBetween(restaurantId,periodStart, periodEnd);
        this.adjustment = 0.0;
        for (RestAdjustment restAdjustment: restAdjustmentList) {
            this.adjustment = this.adjustment + restAdjustment.getAdjustmentAmount();
        }
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getPeriodRange() {
        return Utility.dateRangeFormatted(periodStart,periodEnd);
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
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

    public Double getCommissionForPayout() {
        return Utility.getInstance().round(commissionForPayout,2);
    }

    public Double getAdjustment() {
        return Utility.getInstance().round(adjustment,2);
    }

    public Double getPaidToVendor() {
        return Utility.getInstance().round(paidToVendor,2);
    }

    public void setPaidToVendor(Double paidToVendor) {
        this.paidToVendor = paidToVendor;
    }

    public Double getOwingToVendor() {
        //TODO:: for autoload false - calc at day/task level and add up so the restaurant setting for that day can be used
        this.owingToVendor = this.payoutTotalSale - this.commissionForPayout - this.commissionPerDelivery - this.deliveryFeeFromVendor - this.adjustment - this.prePaidTotalSale;
        return Utility.getInstance().round(owingToVendor,2);
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public List<RestPayoutItem> getPaidRestItems() {
        return paidRestItems;
    }

    public Boolean hasPaidRestItems(){
        if(getPaidRestItems().size()>0){
            return true;
        }else{
            return false;
        }
    }

    public List<RestPayoutItem> getPayoutRestItems() {
        return payoutRestItems;
    }

    public List<RestPayoutItem> getCancelledRestItems() {
        return cancelledRestItems;
    }

    public List<RestAdjustment> getRestAdjustmentList() {
        return restAdjustmentList;
    }

    public Double getDeliveryFeeFromExternal() {
        return Utility.getInstance().round(deliveryFeeFromExternal,2);
    }

    public String getDeliveryFeeFromExternalVendorName() {
        return deliveryFeeFromExternalVendorName;
    }

    public Double getCommissionPerDelivery() {
        return Utility.getInstance().round(commissionPerDelivery,2);
    }

    public VerticalLayout getMainLayout() {
        return mainLayout;
    }

    public Boolean hasPayoutFromExternalVendor(){
        if(deliveryFeeFromExternalCount>0){
            return true;
        }else{
            return false;
        }
    }

    public RestPayoutFromExternalVendor getRestPayoutFromExternalVendor(){
        if(deliveryFeeFromExternalCount>0){
            return new RestPayoutFromExternalVendor(deliveryFeeFromExternalVendorName, deliveryFeeFromExternalCount, deliveryFeeFromExternal);
        }
        return null;
    }

    public File getPdfFile() {
        return pdfFile;
    }

    public String getPdfFileName() {
        return pdfFileName;
    }

    public String getRestaurantEmail() {
        return restaurantEmail;
    }

    public List<RestPayoutItem> getSaleItemsDirect() {
        return saleItemsDirect;
    }

    public Boolean hasDirectSales(){
        if(getSaleItemsDirect().size()>0){
            return true;
        }else{
            return false;
        }
    }

    public Integer getDirectSalesCount(){
        return saleItemsDirect.size();
    }

    public Double getDirectSale() {
        return Utility.getInstance().round(directSale,2);
    }

    public Double getDirectTotalSale() {
        return Utility.getInstance().round(directTotalSale,2);
    }



    public List<RestPayoutItem> getSaleItemsPhoneIn() {
        return saleItemsPhoneIn;
    }

    public Boolean hasPhoneInSales(){
        if(getSaleItemsPhoneIn().size()>0){
            return true;
        }else{
            return false;
        }
    }

    public Integer getPhoneInSalesCount(){
        return saleItemsPhoneIn.size();
    }

    public Double getPhoneInTotalSale() {
        return Utility.getInstance().round(phoneInTotalSale,2);
    }


    public List<RestPayoutItem> getSaleItemsWebOrder() {
        return saleItemsWebOrder;
    }

    public Boolean hasWebOrderSales(){
        if(getSaleItemsWebOrder().size()>0){
            return true;
        }else{
            return false;
        }
    }

    public Integer getWebOrderSalesCount(){
        return saleItemsWebOrder.size();
    }

    public Double getWebOrderTotalSale() {
        return Utility.getInstance().round(webOrderTotalSale,2);
    }

    public List<RestPayoutItem> getSaleItemsWebOrderOnline() {
        return saleItemsWebOrderOnline;
    }

    public Boolean hasWebOrderOnlineSales(){
        if(getSaleItemsWebOrderOnline().size()>0){
            return true;
        }else{
            return false;
        }
    }

    public Integer getWebOrderOnlineSalesCount(){
        return saleItemsWebOrderOnline.size();
    }

    public Double getWebOrderOnlineTotalSale() {
        return Utility.getInstance().round(webOrderOnlineTotalSale,2);
    }

    public Boolean hasPayout() {
        return hasPayout;
    }

    public Boolean hasNonDirectSales(){
        if(getPhoneInSalesCount()>0 || getWebOrderSalesCount()>0 || getWebOrderOnlineSalesCount()>0){
            return Boolean.TRUE;
        }else{
            return Boolean.FALSE;
        }
    }

    public Double getPrePaidTotalSale() {
        return Utility.getInstance().round(prePaidTotalSale,2);
    }

    public List<RestSaleSummary> getRestSaleSummaryList() {
        return restSaleSummaryList;
    }

    public static HorizontalLayout getExternalVendorItem(RestPayoutFromExternalVendor restPayoutFromExternalVendor){
        HorizontalLayout externalVendorLayoutFields = UIUtilities.getHorizontalLayout();
        TextField externalVendorName = UIUtilities.getTextFieldRO("Vendor to invoice", restPayoutFromExternalVendor.getName(),"300px");
        TextField externalVendorCount = UIUtilities.getTextFieldRO("Count", restPayoutFromExternalVendor.getCount().toString(),"100px");
        NumberField externalVendorAmount = UIUtilities.getNumberField("Fee from Vendor", restPayoutFromExternalVendor.getAmount());
        externalVendorLayoutFields.add(externalVendorName,externalVendorCount,externalVendorAmount);
        return externalVendorLayoutFields;
    }

    public Double getCOGS(){
        Double cogs = 0.0;
        if(restaurant.getRestaurantId().equals(0L)){  //Custom orders = ReceiptTotal which is Sale
            cogs = getSale();
        }else{
            //COGS is owingToVendor at minimum - less taxes paid as those get repaid to vendor
            if(getOwingToVendor()>0) cogs = getOwingToVendor() - getTaxes();
            //COGS includes POS orders so ADD the paidToVendor amount
            //Note: restaurant returns the following...
            //  - for payouts - the current record based on the start date of the period
            //  - for period summaries - the non expired record
            // Given this period summaries may not match payouts when restaurant rules change

            if(restaurant.getPosGlobal()){
                cogs = cogs + getPaidToVendor();
            }else if(restaurant.getPosPhonein()){
                cogs = cogs + getPaidToVendor();
            }
            //log.info("getCOGS:" + getRestaurantName() + " getOwingToVendor: " + getOwingToVendor() + " getTaxes:" + getTaxes() + " PaidToVendor:" + getPaidToVendor() + " COGS:" + cogs);
        }
        return Utility.getInstance().round(cogs,2);
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

    public List<TaskEntity> getTaskEntityList() {
        return taskEntityList;
    }

    public void createStatement(){
        log.info("createPDFStatements: restaurant:" + restaurantName);
        File outputFile = new File(outputDir,pdfFileName);
        pdfFile = outputFile;

        try {
            // 1) Load Docx file by filling Velocity template engine and cache it to the registry
            InputStream in = resourcePayStatementTemplate.getInputStream();
            IXDocReport report = XDocReportRegistry.getRegistry().loadReport(in, TemplateEngineKind.Velocity);

            // 2) Create context Java model
            IContext context = report.createContext();
            context.put("restPayoutPeriod", this);

            FieldsMetadata metadata = new FieldsMetadata();
            // 2) Create fields metadata to manage lazy loop (#forech velocity)
            // for table row.
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentDateFmt()");
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentNote()");
            metadata.addFieldAsList("restAdjustmentList.getAdjustmentAmountFmt()");

            context.put("restAdjustmentList", restAdjustmentList);

            // 2) Create fields metadata to manage lazy loop (#forech velocity) for table row.
            metadata.addFieldAsList("saleItemsDirect.getCreationDateTimeFmt()");
            metadata.addFieldAsList("saleItemsDirect.getOrderId()");
            metadata.addFieldAsList("saleItemsDirect.getSale()");
            metadata.addFieldAsList("saleItemsDirect.getTaxes()");
            metadata.addFieldAsList("saleItemsDirect.getTotalSale()");
            metadata.addFieldAsList("saleItemsDirect.getDeliveryFee()");
            metadata.addFieldAsList("saleItemsDirect.getDeliveryFeeFromVendor()");
            metadata.addFieldAsList("saleItemsDirect.getPaymentMethod()");
            report.setFieldsMetadata(metadata);
            context.put("saleItemsDirect", saleItemsDirect);

            // 2) Create fields metadata to manage lazy loop (#forech velocity) for table row.
            metadata.addFieldAsList("saleItemsPhoneIn.getCreationDateTimeFmt()");
            metadata.addFieldAsList("saleItemsPhoneIn.getOrderId()");
            metadata.addFieldAsList("saleItemsPhoneIn.getSale()");
            metadata.addFieldAsList("saleItemsPhoneIn.getTaxes()");
            metadata.addFieldAsList("saleItemsPhoneIn.getTotalSale()");
            metadata.addFieldAsList("saleItemsPhoneIn.getDeliveryFee()");
            metadata.addFieldAsList("saleItemsPhoneIn.getDeliveryFeeFromVendor()");
            metadata.addFieldAsList("saleItemsPhoneIn.getPaymentMethod()");
            report.setFieldsMetadata(metadata);
            context.put("saleItemsPhoneIn", saleItemsPhoneIn);

            // 2) Create fields metadata to manage lazy loop (#forech velocity) for table row.
            metadata.addFieldAsList("saleItemsWebOrder.getCreationDateTimeFmt()");
            metadata.addFieldAsList("saleItemsWebOrder.getOrderId()");
            metadata.addFieldAsList("saleItemsWebOrder.getSale()");
            metadata.addFieldAsList("saleItemsWebOrder.getTaxes()");
            metadata.addFieldAsList("saleItemsWebOrder.getTotalSale()");
            metadata.addFieldAsList("saleItemsWebOrder.getDeliveryFee()");
            metadata.addFieldAsList("saleItemsWebOrder.getDeliveryFeeFromVendor()");
            metadata.addFieldAsList("saleItemsWebOrder.getPaymentMethod()");
            report.setFieldsMetadata(metadata);
            context.put("saleItemsWebOrder", saleItemsWebOrder);

            // 2) Create fields metadata to manage lazy loop (#forech velocity) for table row.
            metadata.addFieldAsList("saleItemsWebOrderOnline.getCreationDateTimeFmt()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getOrderId()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getSale()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getPrePaidTotalSale()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getTaxes()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getTotalSale()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getDeliveryFee()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getDeliveryFeeFromVendor()");
            metadata.addFieldAsList("saleItemsWebOrderOnline.getPaymentMethod()");
            report.setFieldsMetadata(metadata);
            context.put("saleItemsWebOrderOnline", saleItemsWebOrderOnline);

            // 3) Generate report by merging Java model with the Docx
            //To PDF
            OutputStream out = new FileOutputStream(outputFile);
            Options options = Options.getTo(ConverterTypeTo.PDF).via(ConverterTypeVia.XWPF);
            report.convert(context, options, out);
        } catch (IOException e) {
            log.info("createPDFStatements: FAILED vendor:" + restaurantName + " ERROR:" + e.toString());
            e.printStackTrace();
        } catch (XDocReportException e) {
            log.info("createPDFStatements: FAILED2 vendor:" + restaurantName + " ERROR:" + e.toString());
            e.printStackTrace();
        }


    }



    @Override
    public String toString() {
        return "RestPayoutPeriod{" +
                "periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", restaurantId=" + restaurantId +
                ", restaurantName='" + restaurantName + '\'' +
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
                ", commissionForPayout=" + getCommissionForPayout() +
                ", commissionPerDelivery=" + getCommissionPerDelivery() +
                ", adjustment=" + getAdjustment() +
                ", owingToVendor=" + getOwingToVendor() +
                ", restaurant=" + restaurant +
                ", paidRestItems=" + paidRestItems +
                ", payoutRestItems=" + payoutRestItems +
                '}';
    }



}
