package ca.admin.delivermore.views.tasks;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.componentfactory.DateRange;
import com.vaadin.componentfactory.EnhancedDateRangePicker;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoIcon;

import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.data.report.TaskEditDialog;
import ca.admin.delivermore.data.report.TaskListRefreshNeededListener;
import ca.admin.delivermore.gridexporter.ButtonsAlignment;
import ca.admin.delivermore.gridexporter.GridExporter;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Task List")
@Route(value = "tasklist", layout = MainLayout.class)
@RolesAllowed({"ADMIN","MANAGER"})
public class TaskListView extends Main implements TaskListRefreshNeededListener {
    private Logger log = LoggerFactory.getLogger(TaskListView.class);
    private TaskEditDialog taskEditDialog;
    private VerticalLayout mainLayout = UIUtilities.getVerticalLayout();
    private EnhancedDateRangePicker rangeDatePicker = new EnhancedDateRangePicker("Date range:");
    private LocalDate startDate;
    private LocalDate endDate;
    private List<TaskEntity> entityList = new ArrayList<>();
    private Grid<TaskEntity> grid = new Grid<>();

    //filter fields
    private Select<String> filterFieldRestaurant = new Select<>();
    private Collection<String> filterListRestaurant = new TreeSet<>(Collator.getInstance());
    private Select<String> filterFieldDriver = new Select<>();
    private Collection<String> filterListDriver = new TreeSet<>(Collator.getInstance());
    private Select<String> filterFieldType = new Select<>();
    private Collection<String> filterListType = new TreeSet<>(Collator.getInstance());
    private Select<String> filterFieldStatus = new Select<>();
    private Collection<String> filterListStatus = new TreeSet<>(Collator.getInstance());
    private Select<String> filterFieldPaymentMethod = new Select<>();
    private Collection<String> filterListPaymentMethod = new TreeSet<>(Collator.getInstance());
    private NativeLabel countLabel = new NativeLabel();
    private TextField filterFieldJobOrderId = new TextField();
    private TextField filterFieldCustomer = new TextField();
    private TextField filterFieldAddress = new TextField();

    private HorizontalLayout exportLayout = UIUtilities.getHorizontalLayout();

    private TaskDetailRepository taskDetailRepository;
    private RestaurantRepository restaurantRepository;

    public TaskListView(TaskDetailRepository taskDetailRepository, RestaurantRepository restaurantRepository) {
        this.taskDetailRepository = taskDetailRepository;
        this.restaurantRepository = restaurantRepository;
        taskEditDialog = new TaskEditDialog();
        taskEditDialog.addListener(this);
        configureDatePicker();
        startDate = rangeDatePicker.getValue().getStartDate();
        endDate = rangeDatePicker.getValue().getEndDate();
        buildMainLayout();

        setSizeFull();
        mainLayout.setSizeFull();
        add(mainLayout);
        updateList();
    }

    private void buildMainLayout() {
        mainLayout.add(getToolbar());
        mainLayout.add(getFilters());
        mainLayout.add(getGrid());
    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(rangeDatePicker);
        toolbar.setPadding(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        Button filterResetButton = new Button("Clear Filters");
        filterResetButton.addClickListener(event -> {
            clearFilters();
        });
        toolbar.add(filterResetButton);
        //toolbar.add(exportLayout);

        return toolbar;
    }

    private void configureDatePicker() {
        LocalDate defaultDate = LocalDate.parse("2022-08-14");

        //get lastWeek as the default for the range picker
        LocalDate nowDate = LocalDate.now();
        /*
        LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate startOfLastWeek = prevSun.minusWeeks(1);
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);

         */

        rangeDatePicker.setMin(defaultDate);
        rangeDatePicker.setValue(new DateRange(nowDate,nowDate));
        rangeDatePicker.addValueChangeListener(e -> {
            startDate = rangeDatePicker.getValue().getStartDate();
            endDate = rangeDatePicker.getValue().getEndDate();
            if(endDate==null) endDate = startDate;
            updateList();
        });
    }

    private Details getFilters() {
        Details filterDetails = UIUtilities.getDetails();
        FormLayout formLayout = new FormLayout();
        HorizontalLayout filterSummary = UIUtilities.getHorizontalLayout();
        filterSummary.add(new NativeLabel("Filters:"),countLabel);
        filterDetails.setSummary(filterSummary);
        filterDetails.add(formLayout);
        filterFieldRestaurant.setItems(filterListRestaurant);
        String emptyItemName = "All";
        filterFieldRestaurant.setReadOnly(false);
        filterFieldRestaurant.setPlaceholder("Select restaurant");
        filterFieldRestaurant.setEmptySelectionAllowed(true);
        filterFieldRestaurant.setEmptySelectionCaption(emptyItemName);
        filterFieldRestaurant.addValueChangeListener(event -> this.onFilterChange());

        filterFieldDriver.setItems(filterListDriver);
        filterFieldDriver.setReadOnly(false);
        filterFieldDriver.setPlaceholder("Select restaurant");
        filterFieldDriver.setEmptySelectionAllowed(true);
        filterFieldDriver.setEmptySelectionCaption(emptyItemName);
        filterFieldDriver.addValueChangeListener(event -> this.onFilterChange());

        filterFieldType.setItems(filterListType);
        filterFieldType.setReadOnly(false);
        filterFieldType.setPlaceholder("Select type");
        filterFieldType.setEmptySelectionAllowed(true);
        filterFieldType.setEmptySelectionCaption(emptyItemName);
        filterFieldType.addValueChangeListener(event -> this.onFilterChange());

        filterFieldStatus.setItems(filterListStatus);
        filterFieldStatus.setReadOnly(false);
        filterFieldStatus.setPlaceholder("Select status");
        filterFieldStatus.setEmptySelectionAllowed(true);
        filterFieldStatus.setEmptySelectionCaption(emptyItemName);
        filterFieldStatus.addValueChangeListener(event -> this.onFilterChange());

        filterFieldPaymentMethod.setItems(filterListPaymentMethod);
        filterFieldPaymentMethod.setReadOnly(false);
        filterFieldPaymentMethod.setPlaceholder("Select payment method");
        filterFieldPaymentMethod.setEmptySelectionAllowed(true);
        filterFieldPaymentMethod.setEmptySelectionCaption(emptyItemName);
        filterFieldPaymentMethod.addValueChangeListener(event -> this.onFilterChange());

        filterFieldJobOrderId.setClearButtonVisible(true);
        filterFieldJobOrderId.setValueChangeMode(ValueChangeMode.EAGER);
        filterFieldJobOrderId.addValueChangeListener(event -> this.onFilterChange());

        filterFieldCustomer.setClearButtonVisible(true);
        filterFieldCustomer.setValueChangeMode(ValueChangeMode.EAGER);
        filterFieldCustomer.addValueChangeListener(event -> this.onFilterChange());

        filterFieldAddress.setClearButtonVisible(true);
        filterFieldAddress.setValueChangeMode(ValueChangeMode.EAGER);
        filterFieldAddress.addValueChangeListener(event -> this.onFilterChange());

        formLayout.addFormItem(filterFieldRestaurant, "Restaurant" );
        formLayout.addFormItem(filterFieldDriver, "Driver" );
        formLayout.addFormItem(filterFieldType, "Type" );
        formLayout.addFormItem(filterFieldStatus, "Status" );
        formLayout.addFormItem(filterFieldPaymentMethod, "Payment method" );
        formLayout.addFormItem(filterFieldJobOrderId, "Task/Order Id");
        formLayout.addFormItem(filterFieldCustomer, "Customer");
        formLayout.addFormItem(filterFieldAddress, "Address");

        return filterDetails;
    }

    private void onFilterChange(){
        ListDataProvider<TaskEntity> listDataProvider = (ListDataProvider<TaskEntity>) grid.getDataProvider();
        // Since this will be the only active filter, it needs to account for all values of my filter fields
        listDataProvider.setFilter(item -> {
            boolean restaurantFilterMatch = true;
            boolean driverFilterMatch = true;
            Boolean jobidFilterMatch = false;
            Boolean orderidFilterMatch = false;
            boolean idFilterMatch = true;
            boolean customerFilterMatch = true;
            boolean addressFilterMatch = true;
            boolean typeFilterMatch = true;
            boolean statusFilterMatch = true;
            boolean paymentMethodFilterMatch = true;

            if(filterFieldRestaurant.getValue()!=null){
                restaurantFilterMatch = item.getRestaurantName().equals(filterFieldRestaurant.getValue());
            }
            if(filterFieldDriver.getValue()!=null){
                driverFilterMatch = item.getFleetName().equals(filterFieldDriver.getValue());
            }
            if(filterFieldType.getValue()!=null){
                typeFilterMatch = item.getTaskTypeName().equals(filterFieldType.getValue());
            }
            if(filterFieldStatus.getValue()!=null){
                statusFilterMatch = item.getJobStatusName().equals(filterFieldStatus.getValue());
            }
            if(filterFieldPaymentMethod.getValue()!=null){
                paymentMethodFilterMatch = item.getPaymentMethod().equals(filterFieldPaymentMethod.getValue());
            }
            if(filterFieldJobOrderId.getValue()!=null) {
                jobidFilterMatch = item.getJobId().toString().contains(filterFieldJobOrderId.getValue());
                if(item.getOrderId()==null || item.getOrderId().isEmpty()) {
                }else{
                    orderidFilterMatch = item.getOrderId().contains(filterFieldJobOrderId.getValue());
                }
                idFilterMatch = Stream.of(jobidFilterMatch, orderidFilterMatch).anyMatch(Boolean.TRUE::equals);
            }
            if(filterFieldCustomer.getValue()!=null){
                customerFilterMatch = item.getCustomerUsername().toLowerCase().contains(filterFieldCustomer.getValue().toLowerCase());
            }
            if(filterFieldAddress.getValue()!=null){
                addressFilterMatch = item.getJobAddress().toLowerCase().contains(filterFieldAddress.getValue().toLowerCase());
            }
            return restaurantFilterMatch && driverFilterMatch && idFilterMatch && customerFilterMatch && addressFilterMatch && typeFilterMatch && statusFilterMatch && paymentMethodFilterMatch;
        });
        updateCounts();
    }

    private void clearFilters(){
        filterFieldRestaurant.clear();
        filterFieldDriver.clear();
        filterFieldType.clear();
        filterFieldStatus.clear();
        filterFieldPaymentMethod.clear();
        filterFieldJobOrderId.clear();
        filterFieldCustomer.clear();
        filterFieldAddress.clear();
    }

    private VerticalLayout getGrid() {
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout();
        gridLayout.setWidthFull();
        gridLayout.setHeightFull();
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

        exporter.setFileName("TaskExport" + new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime()));
        exporter.setButtonsAlignment(ButtonsAlignment.LEFT);

        gridLayout.add(grid);
        gridLayout.setFlexGrow(1,grid);

        return gridLayout;
    }

    private void updateList(){
        //get list of tasks for date range - then apply filters
        entityList = taskDetailRepository.search(startDate.atStartOfDay(),endDate.atTime(23,59,59));
        //build filter lists
        filterListRestaurant.clear();
        filterListDriver.clear();
        filterFieldType.clear();
        filterFieldStatus.clear();
        filterFieldPaymentMethod.clear();
        for (TaskEntity taskEntity: entityList) {
            if(taskEntity.getRestaurantName()!=null){
                filterListRestaurant.add(taskEntity.getRestaurantName());
            }
            if(taskEntity.getFleetName()!=null){
                filterListDriver.add(taskEntity.getFleetName());
            }
            filterListType.add(taskEntity.getTaskTypeName());
            if(taskEntity.getJobStatusName()!=null){
                filterListStatus.add(taskEntity.getJobStatusName());
            }
            if(taskEntity.getPaymentMethod()!=null){
                filterListPaymentMethod.add(taskEntity.getPaymentMethod());
            }
        }
        filterFieldRestaurant.setItems(filterListRestaurant);
        filterFieldDriver.setItems(filterListDriver);
        filterFieldType.setItems(filterListType);
        filterFieldStatus.setItems(filterListStatus);
        filterFieldPaymentMethod.setItems(filterListPaymentMethod);
        grid.setItems(entityList);
        grid.getDataProvider().refreshAll();
        updateCounts();
    }

    @Override
    public void taskListRefreshNeeded() {
        entityList = taskDetailRepository.search(startDate.atStartOfDay(),endDate.atTime(23,59,59));
        grid.setItems(entityList);
        grid.getDataProvider().refreshAll();
        onFilterChange();
        //updateList();
    }

    private void updateCounts(){
        countLabel.setText("(" + grid.getListDataView().getItemCount() + "/" + entityList.size() + " tasks)");
        //log.info("***updateCounts: getListDataView size:" + grid.getListDataView().getItemCount() + " filtered int:" + filtered);
    }
}
