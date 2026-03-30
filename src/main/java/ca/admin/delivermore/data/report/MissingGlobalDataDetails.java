package ca.admin.delivermore.data.report;

import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.views.UIUtilities;

import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.theme.lumo.LumoIcon;

public class MissingGlobalDataDetails {
    private TaskEditDialog taskEditDialog;
    private List<TaskEntity> missingGlobalDataList = new ArrayList<>();
    private Grid<TaskEntity> missingGlobalDataGrid;
    private TaskDetailRepository taskDetailRepository;

    public MissingGlobalDataDetails() {
        taskDetailRepository = Registry.getBean(TaskDetailRepository.class);
        taskEditDialog = new TaskEditDialog();
    }

    public Details buildMissingGlobalData(LocalDate periodStart, LocalDate periodEnd){
        missingGlobalDataList = taskDetailRepository.getTaskEntityByDateMissingGlobalInfo(periodStart.atStartOfDay(),periodEnd.atTime(23,59,59));
        Details missingGlobalDetails = UIUtilities.getDetails();
        if(missingGlobalDataList.size()>0){
            missingGlobalDetails.setSummaryText("Missing Global Data");
            missingGlobalDetails.setOpened(true);
        }else{
            missingGlobalDetails.setSummaryText("No Missing Global Data");
        }
        missingGlobalDetails.addThemeVariants(DetailsVariant.FILLED);
        missingGlobalDetails.setWidthFull();
        if(missingGlobalDataList.size()>0){
            VerticalLayout missingGlobalGridLayout = UIUtilities.getVerticalLayout();
            missingGlobalDataGrid = new Grid<>();
            missingGlobalDataGrid.setWidthFull();
            missingGlobalDataGrid.setAllRowsVisible(true);
            missingGlobalDataGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
            missingGlobalDataGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
            missingGlobalDataGrid.setItems(missingGlobalDataList);
            missingGlobalDataGrid.addComponentColumn(item -> {
                Icon editIcon = LumoIcon.EDIT.create();
                //Button editButton = new Button("Edit");
                editIcon.addClickListener(e -> {
                    taskEditDialog.setDialogMode(TaskEditDialog.DialogMode.EDIT);
                    taskEditDialog.dialogOpen(item);
                });
                return editIcon;
            }).setWidth("150px").setFlexGrow(0);
            missingGlobalDataGrid.addColumn(TaskEntity::getOrderId).setHeader("OrderId");
            missingGlobalDataGrid.addColumn(TaskEntity::getRestaurantName).setHeader("Restaurant");
            missingGlobalDataGrid.addColumn(TaskEntity::getFleetName).setHeader("Driver");
            missingGlobalDataGrid.addColumn(new LocalDateTimeRenderer<>(TaskEntity::getCreationDate,"MM/dd HH:mm")).setHeader("Date/Time");
            missingGlobalDataGrid.addColumn(TaskEntity::getPaymentMethod).setHeader("Method");
            missingGlobalDataGrid.addColumn(TaskEntity::getGlobalSubtotal).setHeader("Subtotal");
            missingGlobalDataGrid.addColumn(TaskEntity::getGlobalTotalTaxes).setHeader("Taxes");
            missingGlobalDataGrid.addColumn(TaskEntity::getTotalSale).setHeader("Total Sale");
            missingGlobalDataGrid.getColumns().forEach(col -> col.setAutoWidth(true));
            missingGlobalGridLayout.add(missingGlobalDataGrid);
            missingGlobalDetails.add(missingGlobalGridLayout);
        }
        return missingGlobalDetails;
    }

    public TaskEditDialog getTaskEditDialog() {
        return taskEditDialog;
    }
}
