package ca.admin.delivermore.data.report;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.components.custom.ButtonNumberField;
import ca.admin.delivermore.data.entity.RestAdjustment;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.RestAdjustmentRepository;
import ca.admin.delivermore.views.UIUtilities;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

import com.vaadin.flow.theme.lumo.LumoIcon;

public class RestPayoutAdjustmentDialog {
    enum DialogMode{
        NEW, EDIT, NEW_FIXED_REST, DELETE
    }
    //DriverAdjustmentDialog fields
    private Logger log = LoggerFactory.getLogger(RestPayoutAdjustmentDialog.class);
    private Dialog dialog = new Dialog();
    private DialogMode dialogMode = DialogMode.EDIT;
    private ComboBox<Restaurant> dialogRestaurant = new ComboBox<>("Restaurant");
    private DatePicker dialogAdjustmentDate = new DatePicker("Adjustment date");

    private ButtonNumberField dialogAdjustmentAmount = UIUtilities.getButtonNumberField("Adjustment amount",false,"$");
    private TextField dialogAdjustmentNote = new TextField("Adjustment note");
    private Button dialogOkButton = new Button("OK");
    private Button dialogCancelButton = new Button("Cancel");
    private Button dialogCloseButton = new Button(LumoIcon.CROSS.create());
    private LocalDate payoutDate;
    private RestaurantRepository restaurantRepository;
    private RestAdjustment selectedRestAdjustment;
    private RestAdjustmentRepository restAdjustmentRepository;
    private RestPayoutPeriod restPayoutPeriod;
    private RestPayoutSummary restPayoutSummary;

    public RestPayoutAdjustmentDialog(LocalDate payoutDate) {
        this.payoutDate = payoutDate;
        this.restaurantRepository = Registry.getBean(RestaurantRepository.class);
        this.restAdjustmentRepository = Registry.getBean(RestAdjustmentRepository.class);
        dialogConfigure();
    }

    private void dialogConfigure() {
        dialog.getElement().setAttribute("aria-label", "Edit adjustment");

        VerticalLayout dialogLayout = dialogLayout();
        dialog.add(dialogLayout);
        dialog.setHeaderTitle("Restaurant adjustment");

        dialogCloseButton.addClickListener((e) -> dialog.close());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(dialogCloseButton);
        dialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> dialog.close());

        dialogOkButton.addClickListener(
                event -> {
                    dialogOkButton.setText("Wait...");
                    dialogSave();
                }
        );
        dialogOkButton.addClickShortcut(Key.ENTER);
        dialogOkButton.setEnabled(false);
        dialogOkButton.setDisableOnClick(true);

        HorizontalLayout footerLayout = new HorizontalLayout(dialogOkButton,dialogCancelButton);

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayout, () -> {}, Key.ENTER)
                .listenOn(footerLayout);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

        dialog.getFooter().add(footerLayout);
    }

    public void dialogSave() {
        //TODO: process to save adjustments
        log.info("dialogSave:");
        if(dialogMode.equals(DialogMode.DELETE)){
            restAdjustmentRepository.delete(selectedRestAdjustment);
            Notification.show("Deleted");
        }else{
            Integer notifyCounter = 0;
            notifyCounter = 1;
            selectedRestAdjustment.setRestaurantId(dialogRestaurant.getValue().getRestaurantId());
            selectedRestAdjustment.setRestaurantName(dialogRestaurant.getValue().getName());
            if(dialogAdjustmentAmount.getValue()==null){
                selectedRestAdjustment.setAdjustmentAmount(0.0);
            }else{
                selectedRestAdjustment.setAdjustmentAmount(dialogAdjustmentAmount.getNumberField().getValue());
            }
            selectedRestAdjustment.setAdjustmentDate(dialogAdjustmentDate.getValue());
            selectedRestAdjustment.setAdjustmentNote(dialogAdjustmentNote.getValue());
            log.info("dialogSave: saving:" + selectedRestAdjustment);
            restAdjustmentRepository.save(selectedRestAdjustment);
            log.info("dialogSave: save completed");
            String notifyText = "updated";
            if(dialogMode.equals(DialogMode.NEW) || dialogMode.equals(DialogMode.NEW_FIXED_REST)){
                notifyText = "created";
            }
            Notification.show(notifyCounter + " " + notifyText);
        }

        //Refresh
        Boolean summaryLevel = Boolean.FALSE;
        //Refresh the specific period
        if(restPayoutPeriod==null){
            restPayoutPeriod = restPayoutSummary.getPeriod(selectedRestAdjustment.getRestaurantId());
            summaryLevel = Boolean.TRUE;
        }
        if(restPayoutPeriod!=null){
            restPayoutPeriod.refresh();
            if(!summaryLevel){
                restPayoutPeriod.openDetails();
            }
        }
        //refresh the summary
        restPayoutSummary.refresh();

        /*
        //Refresh
        Boolean fullRefresh = Boolean.FALSE;
        if(daDialogMode.equals(DriverPayoutView.DialogMode.NEW) && !driverPayoutPeriod.getFleetIds().contains(selectedDriverAdjustment.getFleetId())){
            //full page refresh required as the adjustment is for a driver that previously was not listed
            log.info("daDialogSave: new Driver record so refreshing full Driver Payout UI");
            fullRefresh = Boolean.TRUE;
        }else if(daDialogMode.equals(DriverPayoutView.DialogMode.DELETE)){
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

         */

        dialog.close();

    }

    public void dialogOpen(RestAdjustment restAdjustment, RestPayoutSummary restPayoutSummary, RestPayoutPeriod restPayoutPeriod){
        this.restPayoutSummary = restPayoutSummary;
        Restaurant selectedRestaurant = null;
        if(restPayoutPeriod!=null){
            selectedRestaurant = restPayoutPeriod.getRestaurant();
        }
        selectedRestAdjustment = restAdjustment;
        this.restPayoutPeriod = restPayoutPeriod;
        dialogRestaurant.setValue(null);
        dialogAdjustmentDate.setValue(payoutDate);
        dialogAdjustmentDate.setMax(null);
        if(dialogMode.equals(DialogMode.NEW)){
            dialogOkButton.setText("OK");
            dialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            dialogRestaurant.setReadOnly(false);
            dialogAdjustmentDate.setReadOnly(false);
            dialogAdjustmentNote.setReadOnly(false);
            dialogAdjustmentAmount.setReadOnly(false);
            dialogAdjustmentNote.setValue("");
            dialogAdjustmentAmount.setValue(0.0);
        }else if(dialogMode.equals(DialogMode.NEW_FIXED_REST)) {
            dialogOkButton.setText("OK");
            dialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            dialogRestaurant.setReadOnly(true);
            dialogAdjustmentDate.setReadOnly(false);
            dialogAdjustmentNote.setReadOnly(false);
            dialogAdjustmentAmount.setReadOnly(false);
            dialogRestaurant.setValue(selectedRestaurant);
            dialogAdjustmentNote.setValue("");
            dialogAdjustmentAmount.setValue(0.0);
        }else if(dialogMode.equals(DialogMode.DELETE)) {
            dialogOkButton.setText("DELETE");
            dialogOkButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            dialogRestaurant.setValue(selectedRestaurant);
            dialogAdjustmentDate.setValue(selectedRestAdjustment.getAdjustmentDate());
            dialogAdjustmentNote.setValue(selectedRestAdjustment.getAdjustmentNote());
            dialogAdjustmentAmount.setValue(selectedRestAdjustment.getAdjustmentAmount());

            dialogRestaurant.setReadOnly(true);
            dialogAdjustmentDate.setReadOnly(true);
            dialogAdjustmentNote.setReadOnly(true);
            dialogAdjustmentAmount.setReadOnly(true);
        }else {
            dialogOkButton.setText("OK");
            dialogOkButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY,ButtonVariant.LUMO_ERROR);
            dialogRestaurant.setValue(selectedRestaurant);
            dialogRestaurant.setReadOnly(true);
            dialogAdjustmentDate.setReadOnly(false);
            dialogAdjustmentNote.setReadOnly(false);
            dialogAdjustmentAmount.setReadOnly(false);
            dialogAdjustmentDate.setValue(selectedRestAdjustment.getAdjustmentDate());
            dialogAdjustmentNote.setValue(selectedRestAdjustment.getAdjustmentNote());
            dialogAdjustmentAmount.setValue(selectedRestAdjustment.getAdjustmentAmount());
        }

        dialogValidate();
        dialog.open();
    }


    private VerticalLayout dialogLayout() {
        dialogRestaurant.setItems(restaurantRepository.getEffectiveRestaurantsForPayout(payoutDate));

        dialogRestaurant.setItemLabelGenerator(Restaurant::getName);
        dialogRestaurant.setReadOnly(true);
        dialogRestaurant.setPlaceholder("Select restaurant");
        dialogRestaurant.addValueChangeListener(item -> {
            dialogValidate();
        });

        dialogAdjustmentNote.addValueChangeListener(item -> {
            dialogValidate();
        });

        dialogAdjustmentDate.addValueChangeListener(item -> {
            dialogValidate();
        });

        dialogAdjustmentAmount.setWidthFull();
        dialogAdjustmentAmount.setButtonIcon(VaadinIcon.PLUS_MINUS.create());
        dialogAdjustmentAmount.addClickListener(e -> {
            dialogAdjustmentAmount.setValue(dialogAdjustmentAmount.getValue()*-1);
        });

        VerticalLayout fieldLayout = new VerticalLayout(dialogRestaurant,dialogAdjustmentDate,dialogAdjustmentNote,dialogAdjustmentAmount);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        return fieldLayout;
    }

    private void dialogValidate() {
        if(dialogRestaurant.isInvalid() || dialogRestaurant.getValue()==null){
            dialogOkButton.setEnabled(false);
        }else if(dialogAdjustmentNote.isInvalid() || dialogAdjustmentNote.getValue()==null || dialogAdjustmentNote.getValue().isEmpty() ){
            dialogOkButton.setEnabled(false);
        }else{
            dialogOkButton.setEnabled(true);
        }
        log.info("dialogValidate: dialogAdjustmentNote: value:" + dialogAdjustmentNote.getValue());
    }

    public DialogMode getDialogMode() {
        return dialogMode;
    }

    public void setDialogMode(DialogMode dialogMode) {
        this.dialogMode = dialogMode;
    }
}
