package ca.admin.delivermore.views.drivers;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.data.entity.DriverAdjustmentTemplate;
import ca.admin.delivermore.data.scheduler.SchedulerEventDialog;
import ca.admin.delivermore.data.service.DriverAdjustmentTemplateRepository;
import ca.admin.delivermore.gridexporter.ButtonsAlignment;
import ca.admin.delivermore.gridexporter.GridExporter;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Driver Adjustment Templates")
@Route(value = "driveradjustmenttemplates", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class DriverAdjustmentTemplateView extends VerticalLayout {

    enum DialogMode{
        NEW, EDIT, DELETE
    }

    private Logger log = LoggerFactory.getLogger(DriverAdjustmentTemplateView.class);
    private VerticalLayout mainLayout = UIUtilities.getVerticalLayout();
    private Dialog adjDialog = new Dialog();
    private DialogMode dialogMode = DialogMode.EDIT;

    private Button dialogOkButton = new Button("OK");
    private Icon okIcon = new Icon("lumo", "checkmark");
    private Icon resetIcon = new Icon("lumo", "undo");

    private Button dialogResetButton = new Button("Reset");
    private Button dialogCancelButton = new Button("Cancel");
    private Button dialogCloseButton = new Button(new Icon("lumo", "cross"));

    private TextField dialogTemplateName = new TextField("Template Name");
    private NumberField dialogTemplateAmount = UIUtilities.getNumberField("Amount", false, "$");

    private Boolean validationEnabled = Boolean.FALSE;
    private Boolean hasChangedValues = Boolean.FALSE;

    Grid<DriverAdjustmentTemplate> grid = new Grid<>();
    private DriverAdjustmentTemplate selectedTemplate = new DriverAdjustmentTemplate();

    private DriverAdjustmentTemplateRepository driverAdjustmentTemplateRepository;
    private List<DriverAdjustmentTemplate> driverAdjustmentTemplateList;

    public DriverAdjustmentTemplateView(DriverAdjustmentTemplateRepository driverAdjustmentTemplateRepository) {
        this.driverAdjustmentTemplateRepository = driverAdjustmentTemplateRepository;
        dialogConfigure();

        mainLayout.add(getToolbar());
        mainLayout.add(getGrid());
        setSizeFull();
        mainLayout.setSizeFull();
        add(mainLayout);
    }

    private HorizontalLayout getToolbar(){
        HorizontalLayout toolbar = UIUtilities.getHorizontalLayout(true,true,false );
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        Button refreshButton = new Button("Refresh");
        refreshButton.addClickListener(e -> {
            refreshGrid();
        });
        Icon addNewIcon = new Icon("lumo", "plus");
        addNewIcon.setColor("green");
        Button addNew = new Button("Add", addNewIcon);
        addNew.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addNew.addClickListener(e -> {
            dialogMode = DialogMode.NEW;
            dialogOpen(new DriverAdjustmentTemplate());
        });

        toolbar.add(addNew,refreshButton);
        return toolbar;
    }

    private VerticalLayout getGrid(){
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout();
        gridLayout.setWidthFull();
        gridLayout.setHeightFull();

        refreshGrid();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setMultiSort(true, Grid.MultiSortPriority.APPEND);
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        GridExporter<DriverAdjustmentTemplate> exporter = GridExporter.createFor(grid);
        Grid.Column editIconColumn = grid.addComponentColumn(item -> {
            Icon editIcon = new Icon("lumo", "edit");
            editIcon.setTooltipText("Edit gift card");
            //Button editButton = new Button("Edit");
            editIcon.addClickListener(e -> {
                dialogMode = DialogMode.EDIT;
                dialogOpen(item);
            });
            return editIcon;
        }).setWidth("40px").setFlexGrow(0).setFrozen(false);
        exporter.setExportColumn(editIconColumn,false);

        Grid.Column deleteIconColumn = grid.addComponentColumn(item -> {
            Icon deleteIcon = new Icon("lumo", "cross");
            deleteIcon.setTooltipText("Delete gift card");
            deleteIcon.setColor("red");
            deleteIcon.addClickListener(e -> {
                confirmDelete(item);
            });
            return deleteIcon;
        }).setWidth("40px").setFlexGrow(0).setFrozen(false);
        exporter.setExportColumn(deleteIconColumn,false);

        exporter.createExportColumn(grid.addColumn(DriverAdjustmentTemplate::getTemplateName).setFlexGrow(1).setSortable(true),true,"Template Name");
        exporter.createExportColumn(grid.addColumn(DriverAdjustmentTemplate::getTemplateAmount).setFlexGrow(0).setSortable(true),true,"Amount");

        exporter.setFileName("DriverAdjustmentTemplatesExport" + new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime()));
        exporter.setButtonsAlignment(ButtonsAlignment.LEFT);

        gridLayout.add(grid);
        gridLayout.setFlexGrow(1,grid);

        return gridLayout;
    }

    private void confirmDelete(DriverAdjustmentTemplate template){
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Template?");
        dialog.setText(
                "Delete template:" + template.getTemplateName() + " with amount:" + template.getTemplateAmount() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            driverAdjustmentTemplateRepository.delete(template);
            refreshGrid();
        });
        dialog.open();
    }

    private void refreshGrid() {
        driverAdjustmentTemplateList = driverAdjustmentTemplateRepository.findAll();
        grid.setItems(driverAdjustmentTemplateList);
        grid.getDataProvider().refreshAll();
    }

    public void dialogConfigure() {
        adjDialog.getElement().setAttribute("aria-label", "Edit driver adjustment templates");

        VerticalLayout dialogLayout = dialogLayout();
        adjDialog.add(dialogLayout);
        adjDialog.setHeaderTitle("Edit Selected Template");

        dialogCloseButton.addClickListener((e) -> adjDialog.close());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        adjDialog.getHeader().add(dialogCloseButton);
        adjDialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> adjDialog.close());

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
        enableOkReset(false);

        HorizontalLayout footerLayout = new HorizontalLayout(dialogOkButton, dialogResetButton,dialogCancelButton);

        adjDialog.getFooter().add(footerLayout);
    }

    private void dialogSave() {
        log.info("dialogSave: called for:" + selectedTemplate.toString());
        if(dialogTemplateAmount.getValue()==null){
            selectedTemplate.setTemplateAmount(0.0);
        }else{
            selectedTemplate.setTemplateAmount(dialogTemplateAmount.getValue());
        }
        selectedTemplate.setTemplateName(dialogTemplateName.getValue());

        driverAdjustmentTemplateRepository.save(selectedTemplate);
        UIUtilities.showNotification("Updated");

        //refresh
        refreshGrid();
        adjDialog.close();

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

    private VerticalLayout dialogLayout() {
        dialogTemplateAmount.setReadOnly(false);
        dialogTemplateAmount.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogTemplateAmount.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogTemplateName.setReadOnly(false);
        dialogTemplateName.setAutofocus(true);
        dialogTemplateName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogTemplateName.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        VerticalLayout fieldLayout = new VerticalLayout(dialogTemplateName,dialogTemplateAmount);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        return fieldLayout;
    }

    private void dialogOpen(DriverAdjustmentTemplate template){
        selectedTemplate = template;
        setValues();
        validationEnabled = Boolean.TRUE;

        dialogValidate();
        adjDialog.open();
    }

    private void setValues(){
        //set values
        if(selectedTemplate.getTemplateName()==null){
            dialogTemplateName.setValue("");
        }else{
            dialogTemplateName.setValue(selectedTemplate.getTemplateName());
        }

        if(selectedTemplate.getTemplateAmount()==null){
            dialogTemplateAmount.setValue(0.0);
        }else{
            dialogTemplateAmount.setValue(selectedTemplate.getTemplateAmount());
        }
    }

    private void dialogValidate() {
        if(validationEnabled && selectedTemplate!=null){
            if(dialogMode.equals(SchedulerEventDialog.DialogMode.NEW)){
                hasChangedValues = Boolean.TRUE;
            }else{
                hasChangedValues = Boolean.FALSE;
                //validate all fields here
                validateField(dialogTemplateAmount,selectedTemplate.getTemplateAmount());

                validateTextField(dialogTemplateName, selectedTemplate.getTemplateName());
            }
        }
        //log.info("dialogValidate: hasChangedValues:" + hasChangedValues + " email:" + hasChangedValuesEmail + " other:" + hasChangedValuesOther);
        if(hasChangedValues){
            enableOkReset(true);
        }else{
            enableOkReset(false);
        }
    }

    private void validateTextField(TextField field, String value){
        if(value==null && field.getValue().isEmpty() || field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }
    private void validateField(NumberField field, Double value){
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


}
