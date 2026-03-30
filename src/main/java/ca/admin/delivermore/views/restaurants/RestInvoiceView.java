package ca.admin.delivermore.views.restaurants;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.data.report.TaskEditDialog;
import ca.admin.delivermore.data.report.TaskListRefreshNeededListener;
import ca.admin.delivermore.gridexporter.ButtonsAlignment;
import ca.admin.delivermore.gridexporter.GridExporter;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import com.vaadin.componentfactory.DateRange;
import com.vaadin.componentfactory.EnhancedDateRangePicker;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.security.RolesAllowed;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@PageTitle("Invoiced Vendors")
@Route(value = "invoicedvendors", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RestInvoiceView extends Main implements TaskListRefreshNeededListener {
    private Logger log = LoggerFactory.getLogger(RestInvoiceView.class);
    LocalDate startDate;
    LocalDate endDate;
    private EnhancedDateRangePicker rangeDatePicker = new EnhancedDateRangePicker("Select period:");

    private VerticalLayout mainLayout = UIUtilities.getVerticalLayout();

    private VerticalLayout detailsLayout = new VerticalLayout();
    private ComboBox<Restaurant> selectedDialogRest = new ComboBox<>("Invoiced Vendor");

    private RestaurantRepository restaurantRepository;
    private Restaurant selectedVendor = null;

    private Boolean diableVendorListChange = Boolean.FALSE;

    private Grid<TaskEntity> grid = new Grid<>();
    private List<TaskEntity> entityList = new ArrayList<>();
    private TaskEditDialog taskEditDialog;
    private TaskDetailRepository taskDetailRepository;
    private NativeLabel countLabel = new NativeLabel();



    public RestInvoiceView(@Autowired RestaurantRepository restaurantRepository, @Autowired TaskDetailRepository taskDetailRepository){
        this.restaurantRepository = restaurantRepository;
        this.taskDetailRepository = taskDetailRepository;
        configureDatePicker();
        startDate = rangeDatePicker.getValue().getStartDate();
        endDate = rangeDatePicker.getValue().getEndDate();
        configureInvoicedVendorList();
        taskEditDialog = new TaskEditDialog();
        taskEditDialog.addListener(this);
        buildMainLayout();

        setSizeFull();
        mainLayout.setSizeFull();
        add(mainLayout);
        updateList();
    }

    private void buildMainLayout() {
        mainLayout.add(getToolbar());
        mainLayout.add(getGrid());
    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(rangeDatePicker, selectedDialogRest, countLabel);
        toolbar.setPadding(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");

        return toolbar;
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
        LocalDate startOfThisWeek = prevSun;
        LocalDate endOfThisWeek = nowDate;

        rangeDatePicker.setMin(defaultDate);
        rangeDatePicker.setValue(new DateRange(startOfThisWeek,endOfThisWeek));
        rangeDatePicker.addValueChangeListener(e -> {
            startDate = rangeDatePicker.getValue().getStartDate();
            endDate = rangeDatePicker.getValue().getEndDate();
            selectedVendor = selectedDialogRest.getValue();
            configureInvoicedVendorList();
            //buildInvoicedVendorDetails();
            updateList();
        });
    }
    private void configureInvoicedVendorList(){
        diableVendorListChange = Boolean.TRUE;
        selectedDialogRest.setItems(restaurantRepository.getEffectiveInvoicedVendor(startDate));
        selectedDialogRest.setItemLabelGenerator(Restaurant::getName);
        selectedDialogRest.setReadOnly(false);
        selectedDialogRest.setPlaceholder("Select vendor");
        if(selectedVendor==null) {
        }else{
            selectedDialogRest.setValue(selectedVendor);
            diableVendorListChange = Boolean.FALSE;
        }
        selectedDialogRest.addValueChangeListener(item -> {
            if(!diableVendorListChange){
                selectedVendor = selectedDialogRest.getValue();
            }
            updateList();
        });
    }


    private VerticalLayout getGrid() {
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout();
        gridLayout.setWidthFull();
        gridLayout.setHeightFull();

        log.info("***SIZE***" + entityList.size());
        for (TaskEntity taskEntity: entityList) {
            log.info("*** Item "+ taskEntity.getCustomerUsername());
        }

        grid.setItems(entityList);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);

        Grid.Column buttonColumn = grid.addComponentColumn(item -> {
            Icon editIcon = LumoIcon.EDIT.create();
            editIcon.addClickListener(e -> {
                taskEditDialog.setDialogMode(TaskEditDialog.DialogMode.EDIT);
                taskEditDialog.dialogOpen(item);
            });
            return editIcon;
        }).setWidth("50px").setFlexGrow(0).setFrozen(true);
        Grid.Column componentColumn = grid.addComponentColumn(item -> {
            return taskEditDialog.getTaskHeader(item, Boolean.TRUE);
        });


        GridExporter<TaskEntity> exporter = GridExporter.createFor(grid);
        exporter.setExportColumn(buttonColumn,false);

        exporter.setExportColumn(componentColumn,false);
        //create hidden columns for the exporter below
        exporter.createExportColumn(grid.addColumn(TaskEntity::getJobId),false,"JobId");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getOrderId),false,"OrderId");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getTaskTypeName),false,"Type");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getJobStatusName),false,"Status");
        exporter.createExportColumn(grid.addColumn(item -> DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm").format(item.getCompletedDate())),false,"Completed");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getRestaurantName),false,"Restaurant");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getCustomerUsername),false,"Customer");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getCustomerPhone),false,"CustomerPhone");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getJobAddress),false,"JobAddress");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getFleetName),false,"Driver");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getWebOrder),false,"WebOrder");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getFeesOnly),false,"FeesOnly");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getReceiptTotal),false,"ReceiptTotal");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getGlobalSubtotal),false,"GlobalSubTotal");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getGlobalTotalTaxes),false,"GlobalTaxes");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getPaymentMethod),false,"PaymentMethod");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getDeliveryFee),false,"DeliveryFee");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getServiceFeePercent),false,"ServiceFeePercent");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getServiceFee),false,"ServiceFee");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getTotalSale),false,"TotalSale");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getPaidToVendor),false,"PaidToVendor");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getTip),false,"Tip");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getTipInNotesIssue),false,"TipIssue");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getNotes),false,"Notes");
        exporter.createExportColumn(grid.addColumn(TaskEntity::getRefNumber),false,"RefNumber");


        exporter.setFileName("TaskExport" + new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime()));
        exporter.setButtonsAlignment(ButtonsAlignment.LEFT);


        gridLayout.add(grid);
        gridLayout.setFlexGrow(1,grid);

        return gridLayout;
    }

    private void updateList(){
        if(!selectedDialogRest.isEmpty()){
            entityList = taskDetailRepository.getTaskEntityByDateAndRestaurant(startDate.atStartOfDay(),endDate.atTime(23,59,59),selectedDialogRest.getValue().getRestaurantId());

        }else {
            entityList.clear();
        }
        //entityList = taskDetailRepository.search(startDate.atStartOfDay(),endDate.atTime(23,59,59));
        grid.setItems(entityList);
        grid.getDataProvider().refreshAll();
        updateCounts();

    }

    private void updateCounts(){
        //total invoice calculation
        Double invoiceTotal = 0.0;
        for (TaskEntity taskEntity: entityList) {
            if(taskEntity.getCommission()!=null){
                invoiceTotal+= taskEntity.getCommission();
            }
        }
        countLabel.setText("(" + entityList.size() + " tasks) Invoice Total: $" + invoiceTotal);

        //log.info("***updateCounts: getListDataView size:" + grid.getListDataView().getItemCount() + " filtered int:" + filtered);
    }
    @Override
    public void taskListRefreshNeeded() {
        entityList = taskDetailRepository.getTaskEntityByDateAndRestaurant(startDate.atStartOfDay(),endDate.atTime(23,59,59),selectedDialogRest.getValue().getRestaurantId());

        //entityList = taskDetailRepository.search(startDate.atStartOfDay(),endDate.atTime(23,59,59));
        grid.setItems(entityList);
        grid.getDataProvider().refreshAll();

    }
}
