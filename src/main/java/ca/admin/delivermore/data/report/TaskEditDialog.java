package ca.admin.delivermore.data.report;

import ca.admin.delivermore.collector.data.Config;
import ca.admin.delivermore.collector.data.Utility;
import ca.admin.delivermore.collector.data.entity.OrderDetail;
import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.TaskEntity;
import ca.admin.delivermore.collector.data.service.OrderDetailRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.components.custom.ButtonNumberField;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.views.UIUtilities;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
import com.vaadin.flow.component.textfield.TextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.theme.lumo.LumoIcon;

public class TaskEditDialog {

    public enum DialogMode{
        EDIT, DELETE
    }

    //default is EDIT - must be superUser to allow DELETE
    private DialogMode dialogMode = DialogMode.EDIT;

    public enum DisplayMode{
        GLOBAL, CUSTOM, PHONEIN
    }
    private DisplayMode displayMode = DisplayMode.GLOBAL;

    private Boolean superUser = Boolean.FALSE;
    private Boolean validationEnabled = Boolean.FALSE;
    private Boolean posGlobal = Boolean.FALSE;
    private Logger log = LoggerFactory.getLogger(TaskEditDialog.class);
    private Dialog dialog = new Dialog();
    private Dialog dialogAdv = new Dialog();
    private Long taskID = 0L;
    private TaskEntity taskEntity;
    private Boolean customTaskConverted = Boolean.FALSE;
    private Button dialogResetButton = new Button("Reset");
    private Button dialogOkButton = new Button("OK");
    private Button dialogCancelButton = new Button("Cancel");
    private Button dialogCloseButton = new Button(LumoIcon.CROSS.create());
    private Button dialogConvertCustom = new Button(VaadinIcon.EXCHANGE.create());

    private Button dialogAdvOkButton = new Button("OK");
    private Button dialogAdvCancelButton = new Button("Cancel");
    private Button dialogAdvCloseButton = new Button(LumoIcon.CROSS.create());


    //Fields defined here
    //global fields
    private NumberField fieldGlobalSubTotal = UIUtilities.getNumberField(Boolean.FALSE);
    private ButtonNumberField fieldGlobalTaxes = UIUtilities.getButtonNumberField("",Boolean.FALSE,"$");

    private Checkbox fieldWebOrder = new Checkbox();
    private Checkbox fieldFeesOnly = new Checkbox();

    private NumberField fieldPaidToVendor = UIUtilities.getNumberField(Boolean.FALSE);
    private NumberField fieldReceiptTotal = UIUtilities.getNumberField(Boolean.FALSE);
    private Select<String> fieldPaymentMethod = new Select<>();
    private NumberField fieldDeliveryFee = UIUtilities.getNumberField(Boolean.FALSE);
    private NumberField fieldServiceFeePercent = UIUtilities.getNumberField("",Boolean.FALSE,"%");
    private NumberField fieldServiceFee = UIUtilities.getNumberField(Boolean.FALSE);
    private ButtonNumberField fieldTotalSale = UIUtilities.getButtonNumberField("",Boolean.FALSE,"$");
    private NumberField fieldTip = UIUtilities.getNumberField(Boolean.FALSE);
    private NumberField fieldTotalWithTip = UIUtilities.getNumberField("",true,"$");
    private Checkbox fieldTipIssue = new Checkbox();
    private TextArea fieldNotes = new TextArea();

    private VerticalLayout dialogLayout = new VerticalLayout();
    private VerticalLayout dialogAdvLayout = new VerticalLayout();

    //adv dialog fields
    private enum AdvDialogMode {
        Global, Form
    }
    private AdvDialogMode advDialogMode = AdvDialogMode.Global;
    private Select<AdvDialogMode> advFieldConvertType = new Select<>();

    private ComboBox<Restaurant> advFieldRestaurant = new ComboBox<>("Restaurant");
    private TextField advFieldOrderId = UIUtilities.getTextField("Order Id");

    private Boolean hasChangedValues = Boolean.FALSE;
    private TaskDetailRepository taskDetailRepository;
    private RestaurantRepository restaurantRepository;

    private List<TaskListRefreshNeededListener> taskListRefreshNeededListeners = new ArrayList<>();

    public TaskEditDialog() {
        taskDetailRepository = Registry.getBean(TaskDetailRepository.class);
        this.restaurantRepository = Registry.getBean(RestaurantRepository.class);
        dialogConfigure();
        dialogAdvConfigure();
    }


    private void dialogConfigure() {
        dialog.getElement().setAttribute("aria-label", "Edit task");

        //configure the dialog internal layout for the form
        dialogLayout.setSpacing(false);
        dialogLayout.setPadding(false);
        dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        dialogLayout.getStyle().set("width", "300px").set("max-width", "100%");

        dialog.add(dialogLayout);
        dialog.setHeaderTitle("Task Edit");

        dialogCloseButton.addClickListener((e) -> dialogClose());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(dialogCloseButton);
        dialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> dialogClose());

        dialogOkButton.addClickListener(
                event -> {
                    dialogSave();
                }
        );
        dialogOkButton.addClickShortcut(Key.ENTER);
        dialogOkButton.setEnabled(false);
        dialogOkButton.setDisableOnClick(true);

        dialogResetButton.addClickListener(
                event -> {
                    dialogOkButton.setEnabled(false);
                    setValues();
                    dialogValidate();
                }
        );

        dialogConvertCustom.setVisible(false);
        dialogConvertCustom.addClickListener(
                event -> {
                    dialogAdvOpen();
                    dialogOkButton.setEnabled(true);
                }
        );

        HorizontalLayout footerLayout = new HorizontalLayout(dialogOkButton,dialogCancelButton,dialogResetButton, dialogConvertCustom);

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayout, () -> {}, Key.ENTER)
                .listenOn(footerLayout);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

        dialog.getFooter().add(footerLayout);

        //one time configuration for any fields
        fieldPaymentMethod.setItems(Config.getInstance().getPaymentMethods());
        fieldPaymentMethod.setReadOnly(false);
        fieldPaymentMethod.setEmptySelectionAllowed(false);
        fieldPaymentMethod.setRequiredIndicatorVisible(true);
        fieldPaymentMethod.setWidth("150px");
        fieldPaymentMethod.setPlaceholder("Select payment method");
        fieldNotes.setReadOnly(true);
        fieldNotes.addThemeVariants(TextAreaVariant.LUMO_SMALL);

        fieldGlobalTaxes.setButtonIcon(VaadinIcon.COGS.create());
        fieldTotalSale.setButtonIcon(VaadinIcon.CALC.create());

        fieldPaidToVendor.addValueChangeListener(item -> dialogValidate());
        fieldReceiptTotal.addValueChangeListener(item -> dialogValidate());
        fieldGlobalSubTotal.addValueChangeListener(item -> dialogValidate());
        fieldGlobalTaxes.addValueChangeListener(item -> dialogValidate());
        fieldGlobalTaxes.addClickListener(
                event -> {
                    dialogTaxesCalc();
                    dialogValidate();
                }
        );
        fieldWebOrder.addValueChangeListener(item -> dialogValidate());
        fieldFeesOnly.addValueChangeListener(item -> dialogValidate());
        fieldPaymentMethod.addValueChangeListener(item -> dialogValidate());
        fieldDeliveryFee.addValueChangeListener(item -> dialogValidate());
        fieldServiceFeePercent.addValueChangeListener(item -> dialogValidate());
        fieldServiceFee.addValueChangeListener(item -> dialogValidate());
        fieldTotalSale.addValueChangeListener(item -> dialogValidate());
        fieldTotalSale.addClickListener(
                event -> {
                    dialogCalc();
                    dialogValidate();
                }
        );
        fieldTip.addValueChangeListener(item -> dialogValidate());
        fieldTipIssue.addValueChangeListener(item -> dialogValidate());

    }

    private void dialogClose(){
        //TODO:: need to somehow refresh the grid
        if(customTaskConverted){
            log.info("dialogClose: reloading taskEntity");
            List<TaskEntity> taskEntityList = taskDetailRepository.findByJobId(this.taskEntity.getJobId());
            log.info("dialogClose: reloading taskEntity:" + taskEntityList);
            if(taskEntityList!=null && taskEntityList.size()>0){
                log.info("dialogClose: reseting to taskEntity:" + taskEntityList.get(0));
                this.taskEntity = taskEntityList.get(0);
            }
        }
        dialog.close();
    }

    private void dialogTaxesCalc(){
        //only valid if shown and only shown if Global
        if(fieldGlobalSubTotal.getValue()!=null){
            fieldGlobalTaxes.setValue(Utility.getInstance().round(fieldGlobalSubTotal.getValue() * 0.05,2));
        }
    }
    private void dialogCalc() {
        Double sourceVal = 0.0;
        if(displayMode.equals(DisplayMode.GLOBAL)){
            sourceVal = fieldGlobalSubTotal.getValue();
        }else{
            sourceVal = fieldReceiptTotal.getValue();
        }
        Double serviceFee = fieldServiceFee.getValue();
        log.info("dialogCalc: sourceVal:" + sourceVal + " serviceFeePercent:" + fieldServiceFeePercent.getValue() + " serviceFee:" + serviceFee);
        if(fieldServiceFeePercent.getValue()!=null){
            serviceFee = Utility.getInstance().round((fieldServiceFeePercent.getValue() * sourceVal)/100,2);
        }
        fieldServiceFee.setValue(serviceFee);

        Double newTotal = 0.0;
        if(displayMode.equals(DisplayMode.GLOBAL)){
            newTotal = sourceVal + fieldGlobalTaxes.getValue() + fieldServiceFee.getValue() + fieldDeliveryFee.getValue();
        }else if(displayMode.equals(DisplayMode.CUSTOM)){
            if(fieldFeesOnly.getValue()){
                //do not add sourceVal
                newTotal = fieldServiceFee.getValue() + fieldDeliveryFee.getValue();
            }else{
                newTotal = sourceVal + fieldServiceFee.getValue() + fieldDeliveryFee.getValue();
            }
        }else{ //phonein
            if(fieldWebOrder.getValue()){
                fieldServiceFeePercent.setValue(0.0);
                fieldServiceFee.setValue(0.0);
                fieldDeliveryFee.setValue(0.0);
                if(fieldPaymentMethod.getValue().equalsIgnoreCase("ONLINE")){
                    newTotal = 0.0;
                }else{
                    newTotal = sourceVal;
                }
            }else{
                newTotal = sourceVal + fieldServiceFee.getValue() + fieldDeliveryFee.getValue();
            }
        }
        fieldTotalSale.setValue(Utility.getInstance().round(newTotal,2));

        if(fieldTip.getValue()==null){
            fieldTotalWithTip.setValue(Utility.getInstance().round(newTotal,2));
        }else{
            fieldTotalWithTip.setValue(Utility.getInstance().round(newTotal + fieldTip.getValue(),2));
        }

    }

    private void dialogSave() {
        //save here
        //update taskEntity from fields
        if(displayMode.equals(DisplayMode.GLOBAL)){
            this.taskEntity.setGlobalSubtotal(fieldGlobalSubTotal.getValue());
            this.taskEntity.setGlobalTotalTaxes(fieldGlobalTaxes.getNumberField().getValue());
            if(posGlobal){
                this.taskEntity.setPaidToVendor(fieldPaidToVendor.getValue());
            }
        }else if(displayMode.equals(DisplayMode.CUSTOM)){
            this.taskEntity.setFeesOnly(fieldFeesOnly.getValue());
            this.taskEntity.setReceiptTotal(fieldReceiptTotal.getValue());
        }else{
            this.taskEntity.setWebOrder(fieldWebOrder.getValue());
            this.taskEntity.setReceiptTotal(fieldReceiptTotal.getValue());
        }
        this.taskEntity.setPaymentMethod(fieldPaymentMethod.getValue());
        this.taskEntity.setDeliveryFee(fieldDeliveryFee.getValue());
        this.taskEntity.setServiceFeePercent(fieldServiceFeePercent.getValue());
        this.taskEntity.setServiceFee(fieldServiceFee.getValue());
        this.taskEntity.setTotalSale(fieldTotalSale.getNumberField().getValue());
        this.taskEntity.setTip(fieldTip.getValue());
        this.taskEntity.setTipInNotesIssue(fieldTipIssue.getValue());

        taskDetailRepository.save(this.taskEntity);
        //refresh if needed
        log.info("dialogSave: notifying listeners");
        dialog.close();
        notifyRefreshNeeded();
    }

    public void dialogOpen(Long jobID){
        List<TaskEntity> taskEntityList = taskDetailRepository.findByJobId(jobID);
        if(taskEntityList!=null && taskEntityList.size()>0){
            dialogOpen(taskEntityList.get(0));
        }else{
            log.info("TaskEditDialog: dialogOpen: failed to find task with jobId:" + jobID);
        }
    }
    public void dialogOpen(TaskEntity taskEntity){
        this.taskEntity = taskEntity;
        //set values and visibility for fields inside the TaskDetailFormLayout class
        dialogLayout.removeAll();
        posGlobal = Boolean.FALSE;
        dialogLayout.add(showTask(this.taskEntity));

        customTaskConverted = Boolean.FALSE;
        dialogValidate();
        dialog.open();


    }

    private void dialogValidate() {
        //validate fields and enable OK button if valid
        if(validationEnabled && this.taskEntity!=null){
            hasChangedValues = Boolean.FALSE;
            if(displayMode.equals(DisplayMode.GLOBAL)){
                log.info("dialogValidate: globalSubtotal:" + this.taskEntity.getGlobalSubtotal() + " field:" + fieldGlobalSubTotal.getValue());
                validateField(fieldGlobalSubTotal,this.taskEntity.getGlobalSubtotal());
                validateField(fieldGlobalTaxes.getNumberField(),this.taskEntity.getGlobalTotalTaxes());
                if(posGlobal){
                    validateField(fieldPaidToVendor,this.taskEntity.getPaidToVendor());
                }
            }else if(displayMode.equals(DisplayMode.CUSTOM)){
                validateField(fieldReceiptTotal,this.taskEntity.getReceiptTotal());
                validateCheckbox(fieldFeesOnly,this.taskEntity.getFeesOnly());
            }else{
                validateField(fieldReceiptTotal,this.taskEntity.getReceiptTotal());
                validateCheckbox(fieldWebOrder,this.taskEntity.getWebOrder());
            }
            //do common fields here
            validateListbox(fieldPaymentMethod,this.taskEntity.getPaymentMethod());
            validateField(fieldDeliveryFee,this.taskEntity.getDeliveryFee());
            validateField(fieldServiceFee,this.taskEntity.getServiceFee());
            validateField(fieldServiceFeePercent,getServiceFeePercent());
            validateField(fieldTotalSale.getNumberField(),this.taskEntity.getTotalSale());
            validateField(fieldTip,this.taskEntity.getTip());
            validateCheckbox(fieldTipIssue,this.taskEntity.getTipInNotesIssue());
            if(customTaskConverted) hasChangedValues = Boolean.TRUE;
        }
        if(hasChangedValues){
            dialogOkButton.setEnabled(true);
            dialogResetButton.setEnabled(true);
        }else{
            dialogOkButton.setEnabled(false);
            dialogResetButton.setEnabled(false);
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

    private void validateCheckbox(Checkbox field, Boolean value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateListbox(Select field, String value){
        log.info("validateListbox: fieldValue:" + field.getValue() + " value:" + value);
        if(field.getValue()==null || field.getValue().equals(value)){
            log.info("validateListbox: matched");
            field.getStyle().set("box-shadow","none");
        }else{
            log.info("validateListbox: NOT matched");
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }


    public DialogMode getDialogMode() {
        return dialogMode;
    }

    public void setDialogMode(DialogMode dialogMode) {
        this.dialogMode = dialogMode;
    }

    public FormLayout showTask(TaskEntity taskEntity){
        validationEnabled = Boolean.FALSE;
        FormLayout taskFormLayout = new FormLayout();
        taskFormLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0px", 1, FormLayout.ResponsiveStep.LabelsPosition.ASIDE));
        /*
        taskFormLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP),
                new FormLayout.ResponsiveStep("100px", 1, FormLayout.ResponsiveStep.LabelsPosition.ASIDE));

         */

        this.taskEntity = taskEntity;

        //determine display mode
        if(this.taskEntity.getCreatedBy().equals(43L)){
            displayMode = DisplayMode.GLOBAL;
            //determine if this restaurant uses pos payment like Smiley's
            List<Restaurant> restaurants = restaurantRepository.findEffectiveByRestaurantId(this.taskEntity.getRestaurantId(),this.taskEntity.getCreationDate().toLocalDate());
            if(restaurants!=null && restaurants.size()>0){
                posGlobal = restaurants.get(0).getPosGlobal();
            }
        }else if(this.taskEntity.getRestaurantId().equals(0L)){ //custom
            displayMode = DisplayMode.CUSTOM;
        }else{
            displayMode = DisplayMode.PHONEIN;
        }

        //configure the field layout
        log.info("showTask: display mode set to:" + displayMode);
        taskFormLayout.removeAll();

        //add the header
        taskFormLayout.add(getTaskHeader(this.taskEntity, Boolean.FALSE));

        //common fields

        //fields by displayMode type
        if(this.displayMode.equals(DisplayMode.GLOBAL)){
            if(posGlobal){
                taskFormLayout.addFormItem(fieldPaidToVendor,"Paid to vendor");
            }
            taskFormLayout.addFormItem(fieldGlobalSubTotal,"Global Subtotal");
            taskFormLayout.addFormItem(fieldGlobalTaxes,"Global Taxes");
            taskFormLayout.addFormItem(fieldPaymentMethod,"Payment");
            taskFormLayout.addFormItem(fieldDeliveryFee,"Delivery Fee");
            taskFormLayout.addFormItem(fieldServiceFeePercent,"Service Fee(%)");
            taskFormLayout.addFormItem(fieldServiceFee,"Service Fee($)");
            taskFormLayout.addFormItem(fieldTotalSale,"Total sale");
            taskFormLayout.addFormItem(fieldTip,"Tip");
            taskFormLayout.addFormItem(fieldTotalWithTip,"Total with tip");
            //TODO:: only add if superUser
            taskFormLayout.addFormItem(fieldTipIssue,"Tip issue");
            taskFormLayout.addFormItem(fieldNotes, "Notes");
            dialogConvertCustom.setVisible(false);
        }else if(this.displayMode.equals(DisplayMode.CUSTOM)){
            taskFormLayout.addFormItem(fieldReceiptTotal,"Receipt total");
            taskFormLayout.addFormItem(fieldFeesOnly,"Fees only");
            taskFormLayout.addFormItem(fieldPaymentMethod,"Payment");
            taskFormLayout.addFormItem(fieldDeliveryFee,"Delivery Fee");
            taskFormLayout.addFormItem(fieldServiceFeePercent,"Service Fee(%)");
            taskFormLayout.addFormItem(fieldServiceFee,"Service Fee($)");
            taskFormLayout.addFormItem(fieldTotalSale,"Total sale");
            taskFormLayout.addFormItem(fieldTip,"Tip");
            taskFormLayout.addFormItem(fieldTotalWithTip,"Total with tip");
            //TODO:: only add if superUser
            taskFormLayout.addFormItem(fieldTipIssue,"Tip issue");
            taskFormLayout.addFormItem(fieldNotes, "Notes");
            dialogConvertCustom.setVisible(true);
        }else{
            //Phonein
            taskFormLayout.addFormItem(fieldReceiptTotal,"Receipt total");
            taskFormLayout.addFormItem(fieldWebOrder,"Web order");
            taskFormLayout.addFormItem(fieldPaymentMethod,"Payment");
            taskFormLayout.addFormItem(fieldDeliveryFee,"Delivery Fee");
            taskFormLayout.addFormItem(fieldServiceFeePercent,"Service Fee(%)");
            taskFormLayout.addFormItem(fieldServiceFee,"Service Fee($)");
            taskFormLayout.addFormItem(fieldTotalSale,"Total sale");
            taskFormLayout.addFormItem(fieldTip,"Tip");
            taskFormLayout.addFormItem(fieldTotalWithTip,"Total with tip");
            //TODO:: only add if superUser
            taskFormLayout.addFormItem(fieldTipIssue,"Tip issue");
            taskFormLayout.addFormItem(fieldNotes, "Notes");
            dialogConvertCustom.setVisible(false);
        }

        //set values
        setValues();
        return taskFormLayout;
    }

    private void setValues(){
        validationEnabled = Boolean.FALSE;
        if(Config.getInstance().getPaymentMethods().contains(this.taskEntity.getPaymentMethod())){
            fieldPaymentMethod.setValue(this.taskEntity.getPaymentMethod());
        }else{
            fieldPaymentMethod.clear();
        }
        fieldDeliveryFee.setValue(this.taskEntity.getDeliveryFee());
        fieldServiceFeePercent.setValue(getServiceFeePercent());
        fieldServiceFee.setValue(this.taskEntity.getServiceFee());
        fieldTotalSale.setValue(this.taskEntity.getTotalSale());
        fieldTip.setValue(this.taskEntity.getTip());
        fieldTotalWithTip.setValue(Utility.getInstance().round(this.taskEntity.getTip() + this.taskEntity.getTotalSale(),2));
        fieldTipIssue.setValue(this.taskEntity.getTipInNotesIssue());
        if(this.taskEntity.getNotes()!=null){
            fieldNotes.setValue(this.taskEntity.getNotes());
        }

        if(this.displayMode.equals(DisplayMode.GLOBAL)){
            fieldGlobalSubTotal.setValue(this.taskEntity.getGlobalSubtotal());
            fieldGlobalTaxes.setValue(this.taskEntity.getGlobalTotalTaxes());
            if(posGlobal){
                fieldPaidToVendor.setValue(this.taskEntity.getPaidToVendor());
            }
        }else if(this.displayMode.equals(DisplayMode.CUSTOM)){
            fieldFeesOnly.setValue(this.taskEntity.getFeesOnly());
            fieldReceiptTotal.setValue(this.taskEntity.getReceiptTotal());
        }else{
            //Phonein
            fieldWebOrder.setValue(this.taskEntity.getWebOrder());
            fieldReceiptTotal.setValue(this.taskEntity.getReceiptTotal());
        }
        validationEnabled = Boolean.TRUE;

    }

    private Double getServiceFeePercent(){
        if(this.taskEntity.getServiceFeePercent()==null){
            if(this.displayMode.equals(DisplayMode.GLOBAL)){
                return Config.getInstance().getServiceFeeGlobal();
            }else if(this.displayMode.equals(DisplayMode.CUSTOM)){
                return Config.getInstance().getServiceFeeCustom();
            }else{
                return Config.getInstance().getServiceFeePhonein();
            }

        }
        return this.taskEntity.getServiceFeePercent();
    }

    public HorizontalLayout getTaskHeader(TaskEntity item, Boolean fullHeader){
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.CENTER);

        Avatar driverName = new Avatar();
        driverName.setName(item.getFleetName());
        driverName.setColorIndex(5);
        driverName.addThemeVariants(AvatarVariant.LUMO_LARGE);
        Avatar taskType = new Avatar();
        taskType.setName(item.getTaskTypeName());
        taskType.setColorIndex(getColorIndex(item.getTaskTypeName()));
        taskType.addThemeVariants(AvatarVariant.LUMO_XSMALL);

        VerticalLayout columnAvatars = new VerticalLayout(driverName,taskType);
        columnAvatars.setPadding(false);
        columnAvatars.setSpacing(true);
        columnAvatars.setWidth("75px");
        columnAvatars.setAlignItems(FlexComponent.Alignment.CENTER);
        columnAvatars.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout columnInfo = new VerticalLayout();
        columnInfo.setPadding(false);
        columnInfo.setSpacing(false);

        Span taskDateTime = new Span(item.getCompletedDate().format(DateTimeFormatter.ofPattern("MM/dd h:mm a")));
        taskDateTime.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        taskDateTime.setWidth("150px");
        //columnInfo.add(taskDateTime);

        Span status = new Span(item.getJobStatusName());
        status.getElement().getThemeList().add(getStatusStyle(item.getJobStatus()));
        //columnInfo.add(status);
        HorizontalLayout dateAndStatus = UIUtilities.getHorizontalLayout(false,true,false);
        dateAndStatus.add(taskDateTime,status);
        columnInfo.add(dateAndStatus);

        Span restaurantName = new Span(item.getRestaurantName());
        columnInfo.add(restaurantName);
        String customerNameString = formatCustomerName(item);
        if(customerNameString!=null){
            Span customerName = new Span(customerNameString);
            customerName.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            columnInfo.add(customerName);
        }

        if(fullHeader){
            Span customerAddress = new Span(item.getJobAddress());
            customerAddress.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            columnInfo.add(customerAddress);
        }

        Span ids = new Span(formatIds(item.getJobId().toString(), item.getOrderId(), item.getRefNumber()));
        ids.getStyle()
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        columnInfo.add(ids);

        if(fullHeader){
            Span paymentInfo;
            if(item.getPaymentMethod()==null){
                paymentInfo = new Span("Total:" + item.getTotalSale() + " Tip:" + item.getTip());
            }else{
                paymentInfo = new Span("Total:" + item.getTotalSale() + " Tip:" + item.getTip() + " - " + item.getPaymentMethod());
            }
            paymentInfo.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");

            columnInfo.add(paymentInfo);
        }

        row.add(columnAvatars, columnInfo);
        row.getStyle().set("line-height", "var(--lumo-line-height-m)");
        return row;
    }

    private Integer getColorIndex(String taskTypeName){
        if(taskTypeName.equals("Global")) return 0;
        if(taskTypeName.equals("Form")) return 1;
        else return 2;
    }

    private String formatIds(String jobId, String orderId, String refNumber){
        String idString = "Jobid:" + jobId ;
        if(orderId!=null && !orderId.isEmpty()){
            idString = idString + " Orderid:" + orderId.trim();
        }
        if(refNumber!=null && !refNumber.isEmpty()){
            idString = idString + " Ref:" + refNumber.trim();
        }
        return idString;
    }

    private String formatCustomerName(TaskEntity item){
        if(item.getCustomerUsername()==null || item.getCustomerUsername().isEmpty()){
            return null;
        }else{
            if(item.getCustomerPhone()==null || item.getCustomerPhone().isEmpty()){
                return item.getCustomerUsername();
            }else{
                return item.getCustomerUsername() + " (ph:" + item.getCustomerPhone() + ")";
            }
        }
    }

    private String getStatusStyle(Long jobStatus){
        String statusString = "badge";
        if(jobStatus.equals(0L)) return "badge";
        if(jobStatus.equals(1L)) return "badge";
        if(jobStatus.equals(2L)) return "badge success";
        if(jobStatus.equals(3L)) return "badge error";
        if(jobStatus.equals(4L)) return "badge";
        //if(jobStatus.equals(5L)) return "";
        if(jobStatus.equals(6L)) return "badge contrast";
        if(jobStatus.equals(7L)) return "badge";
        if(jobStatus.equals(8L)) return "badge error";
        if(jobStatus.equals(9L)) return "badge error";
        if(jobStatus.equals(10L)) return "badge error";
        return "badge error";
    }

    private void dialogAdvConfigure() {
        dialogAdv.getElement().setAttribute("aria-label", "Convert Custom Task");

        //configure the dialog internal layout for the form
        dialogAdvLayout.setSpacing(false);
        dialogAdvLayout.setPadding(false);
        dialogAdvLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        dialogAdvLayout.getStyle().set("width", "400px").set("max-width", "100%");

        dialogAdv.add(dialogAdvLayout);
        dialogAdv.setHeaderTitle("Convert Custom Task");

        dialogAdvCloseButton.addClickListener((e) -> dialogAdv.close());
        dialogAdvCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialogAdv.getHeader().add(dialogAdvCloseButton);
        dialogAdv.setCloseOnEsc(true);
        dialogAdvCancelButton.addClickListener((e) -> dialogAdv.close());

        dialogAdvOkButton.addClickListener(
                event -> {
                    dialogAdvSave();
                }
        );
        dialogAdvOkButton.addClickShortcut(Key.ENTER);
        dialogAdvOkButton.setEnabled(false);
        dialogAdvOkButton.setDisableOnClick(true);

        HorizontalLayout footerLayoutAdv = new HorizontalLayout(dialogAdvOkButton,dialogAdvCancelButton);

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayoutAdv, () -> {}, Key.ENTER)
                .listenOn(footerLayoutAdv);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

        dialogAdv.getFooter().add(footerLayoutAdv);

        //one time configuration for any fields
        List<AdvDialogMode> convertTypeList = Stream.of(AdvDialogMode.values()).collect(Collectors.toList());
        advFieldConvertType.setItems(convertTypeList);
        advFieldConvertType.setPlaceholder("Select conversion type");
        advFieldConvertType.addValueChangeListener(item -> {
            dialogAdvValidate();
        });

        advFieldRestaurant.setItems(restaurantRepository.findDistinctNonExpiredRestaurants());
        advFieldRestaurant.setItemLabelGenerator(Restaurant::getName);
        advFieldRestaurant.setReadOnly(false);
        advFieldRestaurant.setPlaceholder("Select restaurant");
        advFieldRestaurant.addValueChangeListener(item -> {
            dialogAdvValidate();
        });

        advFieldOrderId.addValueChangeListener(item -> {
            dialogAdvValidate();
        });


    }

    private void dialogAdvValidate() {

        if(advFieldConvertType.getValue()==null){
            advFieldOrderId.setEnabled(false);
        }else if(advFieldConvertType.getValue().equals(AdvDialogMode.Global)){
            advFieldOrderId.setEnabled(true);
        }else{
            advFieldOrderId.setEnabled(false);
        }

        if(advFieldRestaurant.getValue().getRestaurantId().equals(0L)){
            dialogAdvOkButton.setEnabled(false);
        }else if(advFieldConvertType.getValue().equals(AdvDialogMode.Global)){
            //Global validation here which includes a valid orderid
            if(advFieldOrderId.getValue()==null || advFieldOrderId.getValue().isEmpty() || advFieldOrderId.getValue().equals("0")){
                dialogAdvOkButton.setEnabled(false);
            }else{
                OrderDetailRepository orderDetailRepository = Registry.getBean(OrderDetailRepository.class);
                Long orderId = Long.valueOf(advFieldOrderId.getValue());
                if(orderId==null){
                    dialogAdvOkButton.setEnabled(false);
                }else{
                    OrderDetail orderDetail = orderDetailRepository.findOrderDetailByOrderId(orderId);
                    if(orderDetail==null){
                        dialogAdvOkButton.setEnabled(false);
                    }else{
                        dialogAdvOkButton.setEnabled(true);
                    }
                }
            }
        }else{  //form does not need order id
            dialogAdvOkButton.setEnabled(true);
        }
    }

    private void dialogAdvSave() {

        if(advFieldConvertType.getValue().equals(AdvDialogMode.Global)){
            this.taskEntity.setTemplateId("Order_Details");
            this.taskEntity.setCreatedBy(43L);
            this.taskEntity.setOrderId(advFieldOrderId.getValue());
            this.taskEntity.setLongOrderId(Long.valueOf(advFieldOrderId.getValue()));
            OrderDetailRepository orderDetailRepository = Registry.getBean(OrderDetailRepository.class);
            OrderDetail orderDetail = orderDetailRepository.findOrderDetailByOrderId(this.taskEntity.getLongOrderId());
            this.taskEntity.updateGlobalData(orderDetail);

        }else{
            this.taskEntity.setCreatedBy(3L);
            this.taskEntity.setOrderId(advFieldRestaurant.getValue().getFormId().toString());
            this.taskEntity.setLongOrderId(advFieldRestaurant.getValue().getFormId());
        }
        this.taskEntity.setPosPayment(false);
        this.taskEntity.setRestaurantId(advFieldRestaurant.getValue().getRestaurantId());
        this.taskEntity.setRestaurantName(advFieldRestaurant.getValue().getName());
        this.taskEntity.updateCalculatedFields();

        //update fields needed for Global Tasks
        customTaskConverted = Boolean.TRUE;

        //dialogOkButton.setEnabled(false);
        dialogLayout.removeAll();
        dialogLayout.add(showTask(this.taskEntity));
        dialogValidate();
        dialogAdv.close();

    }

    private void dialogAdvOpen(){
        customTaskConverted = Boolean.FALSE;
        dialogAdvLayout.removeAll();
        dialogAdvLayout.add(advFieldConvertType, advFieldRestaurant,advFieldOrderId);

        //set values
        List<Restaurant> restaurants = restaurantRepository.findEffectiveByRestaurantId(this.taskEntity.getRestaurantId(), LocalDate.now());
        Restaurant restaurant = restaurants.get(0);
        advFieldRestaurant.setValue(restaurant);

        advFieldConvertType.setValue(AdvDialogMode.Global);

        if(this.taskEntity.getOrderId()!=null){
            advFieldOrderId.setValue(this.taskEntity.getOrderId());
        }

        dialogAdv.open();

    }

    public void addListener(TaskListRefreshNeededListener listener){
        taskListRefreshNeededListeners.add(listener);
    }

    private void notifyRefreshNeeded(){
        for (TaskListRefreshNeededListener listener: taskListRefreshNeededListeners) {
            listener.taskListRefreshNeeded();
        }
    }
}
