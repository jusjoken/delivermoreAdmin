package ca.admin.delivermore.views.restaurants;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.tookan.Team;
import ca.admin.delivermore.components.custom.ListEditor;
import ca.admin.delivermore.components.custom.LocationChoice;
import ca.admin.delivermore.components.custom.LocationChoiceChangedListener;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Restaurants")
@Route(value = "restaurants", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RestView extends VerticalLayout implements LocationChoiceChangedListener {

    enum DialogMode{
        NEW, EDIT, NEW_CLONE, DELETE
    }
    //Restaurant Dialog fields
    private Dialog restDialog = new Dialog();
    private DialogMode dialogMode = DialogMode.EDIT;

    private Button dialogOkButton = new Button("OK");
    private Icon okIcon = new Icon("lumo", "checkmark");
    private Icon resetIcon = new Icon("lumo", "undo");
    private TabSheet dialogTabSheet = new TabSheet();
    private Tab dialogTabEmail = new Tab();
    private Tab dialogTabOther = new Tab();
    private Icon tabEmailIcon = new Icon("vaadin", "exclamation-circle-o");
    private Icon tabOtherIcon = new Icon("vaadin", "exclamation-circle-o");

    private Button dialogResetButton = new Button("Reset");
    private Button dialogCancelButton = new Button("Cancel");
    private Button dialogCloseButton = new Button(new Icon("lumo", "cross"));
    private TextField dialogRestName = new TextField("Restaurant");
    private DatePicker dialogRestEffectiveDate = new DatePicker("Effective Date");
    private TextField dialogRestExpiryDate = new TextField("Expiry Date");
    private IntegerField dialogRestCommission = UIUtilities.getPercentageField("Commission",false);
    private IntegerField dialogRestCommissionPhonein = UIUtilities.getPercentageField("Phonein Commission", false);

    private NumberField dialogRestCommPerDelivery = UIUtilities.getNumberField("Per Delivery", false, "$");
    private NumberField dialogRestCommPerPhonin = UIUtilities.getNumberField("Per Phonein", false, "$");

    private NumberField dialogRestDelFeeFromVendor = UIUtilities.getNumberField("Fee from Vendor", false, "$");
    private IntegerField dialogRestStartDayOffset = UIUtilities.getIntegerField("Start Day Offset",false,0);

    private Checkbox dialogRestPOSGlobal = new Checkbox();
    private Checkbox dialogRestPOSPhonein = new Checkbox();

    private Checkbox dialogRestActiveForPayout = new Checkbox();
    private Checkbox dialogRestInvoicedVendor = new Checkbox();
    private Checkbox dialogRestProcessOrderText = new Checkbox();

    private TextField dialogRestGlobalAuthCode = UIUtilities.getTextField("Global Code");
    private IntegerField dialogRestFormId = UIUtilities.getIntegerField("Form Id",false,0);

    private Select<Team> dialogRestLocation = new Select<>();

    private ListEditor dialogRestEmailEditor = new ListEditor();

    private Dialog newRestDialog = new Dialog();
    private IntegerField newRestDialogId = new IntegerField("Restaurant Id");
    private TextField newRestDialogName = new TextField("Restaurant Name");
    private DatePicker newRestDialogEffectiveDate = new DatePicker("Effective Date");
    private Button newRestDialogOkButton = new Button("OK");
    private Boolean validationEnabled = Boolean.FALSE;
    private Boolean hasChangedValues = Boolean.FALSE;
    private Boolean hasChangedValuesEmail = Boolean.FALSE;
    private Boolean hasChangedValuesOther = Boolean.FALSE;

    Grid<Restaurant> grid = new Grid<>();
    private Restaurant selectedRestaurant = new Restaurant();
    private Restaurant originalRestaurantThatWasCloned = new Restaurant();

    private DatePicker datePicker = new DatePicker(LocalDate.now());

    private LocationChoice locationChoice = new LocationChoice(true);

    RestaurantRepository restaurantRepository;

    private Logger log = LoggerFactory.getLogger(RestView.class);

    public RestView(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
        log.info("Configuring the dialog");
        dialogConfigure();
        configureNewRestDialog();

        grid.removeAllColumns();
        grid.addComponentColumn(item -> {
            Icon editIcon = new Icon("lumo", "edit");
            //Button editButton = new Button("Edit");
            editIcon.addClickListener(e -> {
                if(item.getDateExpired()==null){
                    editRestCurrent(item);
                }else{
                    editRestExpired(item);
                }
            });
            return editIcon;
        }).setWidth("50px").setFlexGrow(0).setFrozen(true);

        grid.addComponentColumn(item -> {
            Icon detailsDeleteIcon = new Icon("lumo", "cross");
            detailsDeleteIcon.setTooltipText("Delete restaurant entry");
            detailsDeleteIcon.setColor("red");
            detailsDeleteIcon.addClickListener(e -> {
                //confirm delete
                confirmDeleteRest(item);
            });
            return detailsDeleteIcon;
        }).setWidth("50px").setFlexGrow(0);

        grid.addColumn(Restaurant::getName)
                .setFlexGrow(0)
                .setHeader("Name").setFrozen(true);
        grid.addColumn(Restaurant::getRestaurantId).setHeader("Id");
        grid.addColumn(item -> {
            return locationChoice.getLocationNameById(item.getTeamId());
        }).setHeader("Location");
        grid.addColumn(item -> {
            return getCommissionFormatted(item.getCommissionRate());
        }).setHeader("Commission").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(item -> {
            return getCommissionFormatted(item.getCommissionRatePhonein());
        }).setHeader("Phonein Commission").setTextAlign(ColumnTextAlign.END);
        grid.addColumn(Restaurant::getDateEffective).setHeader("Effective");
        grid.addColumn(item -> {
            if(item.getDateExpired()==null) return "";
            return item.getDateExpired();
        }).setHeader("Expired");
        grid.addColumn(Restaurant::getEmail)
                .setFlexGrow(0)
                .setWidth("250px")
                .setHeader("Email");
        grid.addColumn(Restaurant::getCommissionPerDelivery).setHeader("Per Delivery");
        grid.addColumn(Restaurant::getCommissionPerPhonein).setHeader("Per Phonein");
        grid.addColumn(Restaurant::getDeliveryFeeFromVendor).setHeader("Fee from Vendor");
        grid.addColumn(Restaurant::getStartDayOffset).setHeader("Startday Offset");
        String statusWidth = "100px";
        grid.addComponentColumn(restaurant -> createCheckIcon(restaurant.getPosGlobal())).setWidth(statusWidth).setComparator(Restaurant::getPosGlobal).setHeader("POS Global");
        grid.addComponentColumn(restaurant -> createCheckIcon(restaurant.getPosPhonein())).setWidth(statusWidth).setComparator(Restaurant::getPosPhonein).setHeader("POS Other");
        grid.addComponentColumn(restaurant -> createCheckIcon(restaurant.getActiveForPayout())).setWidth(statusWidth).setComparator(Restaurant::getActiveForPayout).setHeader("Active for payout");
        grid.addComponentColumn(restaurant -> createCheckIcon(restaurant.getUseInvoiceProcessing())).setWidth(statusWidth).setComparator(Restaurant::getUseInvoiceProcessing).setHeader("Invoiced Vendor");
        grid.addComponentColumn(restaurant -> createCheckIcon(restaurant.getProcessOrderText())).setWidth(statusWidth).setComparator(Restaurant::getProcessOrderText).setHeader("Add order details");
        grid.addColumn(Restaurant::getGlobalAuthCode).setHeader("Global Code");
        grid.addColumn(Restaurant::getFormId).setHeader("Form Id");

        grid.setColumnReorderingAllowed(true);
        //grid.getColumns().forEach(col -> col.setAutoWidth(true));
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        // layout configuration
        setSizeFull();
        configureDatePicker();
        add(getToolbar());
        add(grid);
        locationChoice.addListener(this);
        grid.setItems(restaurantRepository.getEffectiveRestaurants(LocalDate.now()));

    }

    private void confirmDeleteRest(Restaurant item) {

        ConfirmDialog confirmRestDeleteDialog = new ConfirmDialog();
        confirmRestDeleteDialog.setHeader("Delete Restaurant Entry?");
        confirmRestDeleteDialog.setCancelable(true);
        confirmRestDeleteDialog.setConfirmText("Delete");
        confirmRestDeleteDialog.setConfirmButtonTheme("error primary");
        confirmRestDeleteDialog.setText(
                "Delete restaurant: '" + item.getName() + "' with effective date: " + item.getDateEffective() + "?");
        confirmRestDeleteDialog.addConfirmListener(event -> {
            restaurantRepository.delete(item);
            refreshGrid();
        });
        confirmRestDeleteDialog.open();
    }

    private Icon createCheckIcon(Boolean checked) {
        Icon icon;
        if(checked==null) checked = Boolean.FALSE;
        if (checked) {
            icon = VaadinIcon.CHECK.create();
            icon.getElement().getThemeList().add("badge success");
        } else {
            icon = VaadinIcon.CLOSE_SMALL.create();
            icon.getElement().getThemeList().add("badge error");
        }
        icon.getStyle().set("padding", "var(--lumo-space-xs");
        return icon;
    }



    private void editRestExpired(Restaurant item) {
        log.info("Edit called for EXPIRED restaurant. Confirm edit of expired record. Expiry:" + item.getDateExpired());
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Confirm edit of expired record");
        Html text = new Html("<p>Are you sure you want to edit this expired record?<br><br>Restaurant: " + item.getName()
                + "<br>Effective: " + item.getDateEffective()
                + "<br>Expired: " + item.getDateExpired()
                + "<br><strong>Note: changing an expired record can affect previously run payouts and reports!</strong>"
                + "</p>");
        dialog.setText(text);
        dialog.setCancelable(true);
        dialog.setConfirmText("Confirm Edit");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            dialogMode = DialogMode.EDIT;
            dialogOpen(item);
        });
        dialog.open();

    }

    private void editRestCurrent(Restaurant item) {
        log.info("Edit called for CURRENT restaurant...ask to Clone");
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Edit or Clone of current record");
        Html text = new Html("<p>Are you sure you want to edit this current record?<br><br>Restaurant: " + item.getName()
                + "<br>Effective: " + item.getDateEffective()
                + "<br><strong>Note: changing payout related fields in a current record is NOT recommended.  For those cases please make the changes to a new Cloned record by selecting Clone below!</strong>"
                + "</p>");
        dialog.setText(text);
        dialog.setCancelable(true);

        dialog.setRejectable(true);
        dialog.setRejectText("Clone");
        dialog.addRejectListener(event -> {
            //Clone the record and edit it
            originalRestaurantThatWasCloned = item;
            Restaurant clonedRestaurant = new Restaurant();
            BeanUtils.copyProperties(item, clonedRestaurant);
            LocalDate nowDate = LocalDate.now();
            LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
            prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            clonedRestaurant.setDateEffective(prevSun);
            dialogMode = DialogMode.NEW_CLONE;
            dialogOpen(clonedRestaurant);
        });

        dialog.setConfirmText("Edit");
        dialog.addConfirmListener(event -> {
            dialogMode = DialogMode.EDIT;
            dialogOpen(item);
        });
        dialog.open();

    }

    private String getCommissionFormatted(Double rate){
        String commission = "";
        if(rate==null || rate.equals(0.0)) return "";
        commission = getDoubleAsInteger(rate) + "%";
        return commission;
    }

    private HorizontalLayout getToolbar(){
        HorizontalLayout toolbar = UIUtilities.getHorizontalLayout(true,true,false );
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        toolbar.add(datePicker);
        Button refreshButton = new Button("Refresh");
        refreshButton.addClickListener(e -> {
            refreshGrid();
        });

        Icon addIcon = new Icon(VaadinIcon.PLUS);
        //addIcon.setSize("20px");
        addIcon.setColor("green");
        addIcon.setTooltipText("Add new entry");
        addIcon.addClickListener(item -> {
            createNewRest();
        });
        MenuBar addItemMB = new MenuBar();
        addItemMB.addItem(addIcon);


        toolbar.add(refreshButton,addItemMB,locationChoice.getMenuBar());
        return toolbar;
    }

    private void configureNewRestDialog(){
        newRestDialog.setHeaderTitle("Add new Restaurant");
        newRestDialog.setCloseOnEsc(true);

        Button newRestDialogCloseButton = new Button(new Icon("lumo", "cross"));;
        newRestDialogCloseButton.addClickListener((e) -> newRestDialog.close());
        newRestDialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newRestDialog.getHeader().add(newRestDialogCloseButton);

        Button newRestDialogCancelButton = new Button("Cancel");
        newRestDialogCancelButton.addClickListener((e) -> newRestDialog.close());

        newRestDialogOkButton.setIcon(okIcon);
        newRestDialogOkButton.setText("Create");
        //newRestDialogOkButton.setAutofocus(true);
        newRestDialogOkButton.addClickListener(
                event -> {
                    //save new and load for edit
                    newRestDialog.close();
                    Long restId = Long.valueOf(newRestDialogId.getValue());
                    Restaurant newRest = new Restaurant(restId,newRestDialogName.getValue(),newRestDialogEffectiveDate.getValue());
                    //Set default values
                    if(locationChoice.isAllLocationsSelected()){
                        newRest.setTeamId(locationChoice.getLocations().get(0).getTeamId());
                    }else{
                        newRest.setTeamId(locationChoice.getSelectedLocationId());
                    }
                    newRest.setCommissionRate(0.15);
                    newRest.setCommissionRatePhonein(0.15);
                    newRest.setCommissionPerPhonein(0.0);
                    newRest.setCommissionPerDelivery(0.0);
                    newRest.setActiveForPayout(true);
                    dialogTabSheet.setSelectedTab(dialogTabOther);
                    dialogMode = DialogMode.NEW;
                    dialogOpen(newRest);
                }
        );
        newRestDialogOkButton.addClickShortcut(Key.ENTER);
        newRestDialogOkButton.setEnabled(false);
        newRestDialogOkButton.setDisableOnClick(true);

        HorizontalLayout footerLayout = new HorizontalLayout(newRestDialogOkButton, newRestDialogCancelButton);
        newRestDialog.getFooter().add(footerLayout);

        VerticalLayout bodyLayout = new VerticalLayout(newRestDialogId,newRestDialogName,newRestDialogEffectiveDate);
        newRestDialogId.addValueChangeListener(e -> {
            newRestDialogValidate();
        });
        newRestDialogName.addValueChangeListener(e -> {
            newRestDialogValidate();
        });
        newRestDialogEffectiveDate.addValueChangeListener(e -> {
            if(e.getValue()==null) newRestDialogEffectiveDate.setValue(e.getOldValue());
            newRestDialogValidate();
        });
        newRestDialog.add(bodyLayout);

    }

    private void createNewRest() {
        newRestEnableOk(false);
        newRestDialogId.clear();
        newRestDialogName.clear();

        LocalDate nowDate = LocalDate.now();
        LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        newRestDialogEffectiveDate.setValue(prevSun);
        newRestDialog.open();
        //newRestDialogEffectiveDate.getElement().executeJs("this.inputElement.value = ''");
        newRestDialogId.setAutofocus(true);

    }

    private void newRestDialogValidate() {
        if(newRestDialogId.isEmpty() || newRestDialogId.getValue().equals(0)){
            newRestEnableOk(false);
        }else{
            if(newRestDialogName.isEmpty()){
                newRestEnableOk(false);
            }else{
                if(newRestDialogEffectiveDate.getValue()==null){
                    newRestEnableOk(false);
                }else{
                    newRestEnableOk(true);
                }
            }
        }

    }

    private void newRestEnableOk(Boolean enable){
        newRestDialogOkButton.setEnabled(enable);
        if(enable){
            okIcon.setColor("green");
         }else{
            okIcon.setColor(UIUtilities.iconColorNotHighlighted);
        }
    }

    private void configureDatePicker(){
        datePicker.setLabel("Effective on Date");
        datePicker.addValueChangeListener(e -> {
            refreshGrid();
        });
    }

    private void refreshGrid() {
        //TODO: filter by locationChoice
        if(locationChoice.isAllLocationsSelected()){
            grid.setItems(restaurantRepository.getEffectiveRestaurants(datePicker.getValue()));
        }else{
            grid.setItems(restaurantRepository.getEffectiveRestaurantsForTeam(locationChoice.getSelectedLocationId(), datePicker.getValue()));
        }
        grid.getDataProvider().refreshAll();
    }

    public void dialogConfigure() {
        restDialog.getElement().setAttribute("aria-label", "Edit restaurant details");

        VerticalLayout dialogLayout = dialogLayout();
        restDialog.add(dialogLayout);
        restDialog.setHeaderTitle("Edit Selected Restaurant");

        dialogCloseButton.addClickListener((e) -> restDialog.close());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        restDialog.getHeader().add(dialogCloseButton);
        restDialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> restDialog.close());

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

        // Prevent click shortcut of the OK button from also triggering when another button is focused
        /*
        ShortcutRegistration shortcutRegistration = Shortcuts
                .addShortcutListener(footerLayout, () -> {}, Key.ENTER)
                .listenOn(footerLayout);
        shortcutRegistration.setEventPropagationAllowed(false);
        shortcutRegistration.setBrowserDefaultAllowed(true);

         */

        restDialog.getFooter().add(footerLayout);
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
        dialogRestName.setReadOnly(false);
        dialogRestName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestEffectiveDate.setReadOnly(false);
        //dialogRestEffectiveDate.setEnabled(false);
        dialogRestEffectiveDate.addThemeVariants(DatePickerVariant.LUMO_SMALL);
        dialogRestEffectiveDate.setWidth("150px");
        dialogRestEffectiveDate.addValueChangeListener(e -> {
            if(validationEnabled){
                if(e.getValue()==null) dialogRestEffectiveDate.setValue(e.getOldValue());
                dialogValidate();
            }
        });
        dialogRestExpiryDate.setReadOnly(false);
        dialogRestExpiryDate.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestExpiryDate.setWidth("150px");
        HorizontalLayout dateFieldsLayout = UIUtilities.getHorizontalLayout();
        dateFieldsLayout.add(dialogRestEffectiveDate,dialogRestExpiryDate);

        dialogRestEmailEditor.setSeparator(", ");
        dialogRestEmailEditor.getValueField().addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogRestLocation.setLabel("Location");
        dialogRestLocation.setPlaceholder("Select Location");
        dialogRestLocation.setReadOnly(false);
        dialogRestLocation.addThemeVariants(SelectVariant.LUMO_SMALL);
        dialogRestLocation.setItems(locationChoice.getLocations());
        dialogRestLocation.setItemLabelGenerator(Team::getTeamName);
        dialogRestLocation.addValueChangeListener(e ->{
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout locationFieldsLayout = UIUtilities.getHorizontalLayout(false,true,false);
        locationFieldsLayout.add(dialogRestLocation);

        String halfFieldWidth = "130px";
        dialogRestCommission.setReadOnly(false);
        dialogRestCommission.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestCommission.setWidth(halfFieldWidth);
        dialogRestCommission.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestCommissionPhonein.setReadOnly(false);
        dialogRestCommissionPhonein.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestCommissionPhonein.setWidth(halfFieldWidth);
        dialogRestCommissionPhonein.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout commissionFieldsLayout = UIUtilities.getHorizontalLayout(false,true,false);
        commissionFieldsLayout.add(dialogRestCommission,dialogRestCommissionPhonein);

        dialogRestCommPerDelivery.setReadOnly(false);
        dialogRestCommPerDelivery.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestCommPerDelivery.setWidth(halfFieldWidth);
        dialogRestCommPerDelivery.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestCommPerPhonin.setReadOnly(false);
        dialogRestCommPerPhonin.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestCommPerPhonin.setWidth(halfFieldWidth);
        dialogRestCommPerPhonin.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout commissionPerFieldsLayout = UIUtilities.getHorizontalLayout(false,true,false);
        commissionPerFieldsLayout.add(dialogRestCommPerDelivery,dialogRestCommPerPhonin);

        dialogRestDelFeeFromVendor.setReadOnly(false);
        dialogRestDelFeeFromVendor.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestDelFeeFromVendor.setWidth(halfFieldWidth);
        dialogRestDelFeeFromVendor.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestStartDayOffset.setReadOnly(false);
        dialogRestStartDayOffset.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestStartDayOffset.setWidth(halfFieldWidth);
        dialogRestStartDayOffset.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout fieldsLayout3 = UIUtilities.getHorizontalLayout(false,true,false);
        fieldsLayout3.add(dialogRestDelFeeFromVendor,dialogRestStartDayOffset);

        dialogRestPOSGlobal.setLabel("POS Global");
        dialogRestPOSGlobal.setReadOnly(false);
        dialogRestPOSGlobal.setWidth(halfFieldWidth);
        dialogRestPOSGlobal.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestPOSPhonein.setLabel("POS Other");
        dialogRestPOSPhonein.setReadOnly(false);
        dialogRestPOSPhonein.setWidth(halfFieldWidth);
        dialogRestPOSPhonein.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout fieldsLayout4 = UIUtilities.getHorizontalLayout(false,true,false);
        fieldsLayout4.add(dialogRestPOSGlobal,dialogRestPOSPhonein);

        dialogRestActiveForPayout.setLabel("Active for payout");
        dialogRestActiveForPayout.setReadOnly(false);
        dialogRestActiveForPayout.setWidth(halfFieldWidth);
        dialogRestActiveForPayout.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestInvoicedVendor.setLabel("Invoiced Vendor");
        dialogRestInvoicedVendor.setReadOnly(false);
        dialogRestInvoicedVendor.setWidth(halfFieldWidth);
        dialogRestInvoicedVendor.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout fieldsLayout5a = UIUtilities.getHorizontalLayout(false,true,false);
        fieldsLayout5a.add(dialogRestActiveForPayout,dialogRestInvoicedVendor);

        dialogRestProcessOrderText.setLabel("Add order details");
        dialogRestProcessOrderText.setReadOnly(false);
        dialogRestProcessOrderText.setWidth(halfFieldWidth);
        dialogRestProcessOrderText.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout fieldsLayout5b = UIUtilities.getHorizontalLayout(false,true,false);
        fieldsLayout5b.add(dialogRestProcessOrderText);

        dialogRestGlobalAuthCode.setReadOnly(false);
        dialogRestGlobalAuthCode.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestGlobalAuthCode.setWidth(halfFieldWidth);
        dialogRestGlobalAuthCode.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogRestFormId.setReadOnly(false);
        dialogRestFormId.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogRestFormId.setWidth(halfFieldWidth);
        dialogRestFormId.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        HorizontalLayout fieldsLayout6 = UIUtilities.getHorizontalLayout(false,true,false);
        fieldsLayout6.add(dialogRestGlobalAuthCode,dialogRestFormId);

        VerticalLayout otherFieldsLayout = UIUtilities.getVerticalLayout();
        otherFieldsLayout.add(locationFieldsLayout,commissionFieldsLayout,commissionPerFieldsLayout,fieldsLayout3,fieldsLayout4,fieldsLayout5a,fieldsLayout5b,fieldsLayout6);
        dialogTabEmail = new Tab(tabEmailIcon, new Span("Email"));
        dialogTabOther = new Tab(tabOtherIcon, new Span("Other"));
        dialogTabSheet.add(dialogTabEmail, dialogRestEmailEditor);
        dialogTabSheet.add(dialogTabOther, otherFieldsLayout);

        VerticalLayout fieldLayout = new VerticalLayout(dialogRestName,dateFieldsLayout,dialogTabSheet);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        return fieldLayout;
    }

    private void dialogOpen(Restaurant restaurant){
        selectedRestaurant = restaurant;
        if(dialogMode.equals(DialogMode.NEW_CLONE)){
            log.info("dialogOpen: clone   :" + selectedRestaurant);
            log.info("dialogOpen: original:" + originalRestaurantThatWasCloned);
            dialogRestEffectiveDate.setReadOnly(false);
            dialogRestEffectiveDate.setRequired(true);
            dialogRestEffectiveDate.setMin(originalRestaurantThatWasCloned.getDateEffective().plusDays(1));
        }else{
            dialogRestEffectiveDate.setReadOnly(false);
            dialogRestEffectiveDate.setRequired(false);
            dialogRestEffectiveDate.setMin(selectedRestaurant.getDateEffective());
        }
        setValues();
        validationEnabled = Boolean.TRUE;

        dialogValidate();
        restDialog.open();
    }

    private void setValues(){
        //set values
        dialogRestName.setValue(selectedRestaurant.getName());
        dialogRestEffectiveDate.setValue(selectedRestaurant.getDateEffective());
        if(selectedRestaurant.getDateExpired()==null){
            dialogRestExpiryDate.setValue("");
        }else{
            dialogRestExpiryDate.setValue(selectedRestaurant.getDateExpired().toString());
        }
        if(selectedRestaurant.getTeamId()==null){
            dialogRestLocation.setValue(null);
        }else{
            dialogRestLocation.setValue(locationChoice.getLocationTeamById(selectedRestaurant.getTeamId()));
        }

        if(selectedRestaurant.getCommissionRate()==null || selectedRestaurant.getCommissionRate().equals(0.0)){
            dialogRestCommission.setValue(0);
        }else{
            dialogRestCommission.setValue(getDoubleAsInteger(selectedRestaurant.getCommissionRate()));
        }
        if(selectedRestaurant.getCommissionRatePhonein()==null || selectedRestaurant.getCommissionRatePhonein().equals(0.0)){
            dialogRestCommissionPhonein.setValue(0);
        }else{
            dialogRestCommissionPhonein.setValue(getDoubleAsInteger(selectedRestaurant.getCommissionRatePhonein()));
        }

        if(selectedRestaurant.getCommissionPerDelivery()==null){
            dialogRestCommPerDelivery.setValue(0.0);
        }else{
            dialogRestCommPerDelivery.setValue(selectedRestaurant.getCommissionPerDelivery());
        }
        if(selectedRestaurant.getCommissionPerPhonein()==null){
            dialogRestCommPerPhonin.setValue(0.0);
        }else{
            dialogRestCommPerPhonin.setValue(selectedRestaurant.getCommissionPerPhonein());
        }
        if(selectedRestaurant.getDeliveryFeeFromVendor()==null){
            dialogRestDelFeeFromVendor.setValue(0.0);
        }else{
            dialogRestDelFeeFromVendor.setValue(selectedRestaurant.getDeliveryFeeFromVendor());
        }
        dialogRestStartDayOffset.setValue(selectedRestaurant.getStartDayOffset());
        dialogRestPOSGlobal.setValue(selectedRestaurant.getPosGlobal());
        dialogRestPOSPhonein.setValue(selectedRestaurant.getPosPhonein());
        dialogRestActiveForPayout.setValue(selectedRestaurant.getActiveForPayout());
        if(selectedRestaurant.getUseInvoiceProcessing()==null){
            dialogRestProcessOrderText.setValue(Boolean.FALSE);
        }else{
            dialogRestProcessOrderText.setValue(selectedRestaurant.getUseInvoiceProcessing());
        }
        if(selectedRestaurant.getProcessOrderText()==null){
            dialogRestProcessOrderText.setValue(Boolean.FALSE);
        }else{
            dialogRestProcessOrderText.setValue(selectedRestaurant.getProcessOrderText());
        }
        if(selectedRestaurant.getGlobalAuthCode()==null || selectedRestaurant.getGlobalAuthCode().isEmpty()){
            dialogRestGlobalAuthCode.setValue("");
        }else{
            dialogRestGlobalAuthCode.setValue(selectedRestaurant.getGlobalAuthCode());
        }
        if(selectedRestaurant.getFormId()==null){
            dialogRestFormId.setValue(0);
        }else{
            dialogRestFormId.setValue(Math.toIntExact(selectedRestaurant.getFormId()));
        }

        dialogRestEmailEditor.setValue(selectedRestaurant.getEmail());
    }

    private void dialogValidate() {
        if(validationEnabled && selectedRestaurant!=null){
            if(dialogMode.equals(DialogMode.NEW)){
                hasChangedValues = Boolean.TRUE;
                hasChangedValuesEmail = Boolean.TRUE;
                hasChangedValuesOther = Boolean.TRUE;
            }else{
                hasChangedValues = Boolean.FALSE;
                hasChangedValuesEmail = Boolean.FALSE;
                hasChangedValuesOther = Boolean.FALSE;
                //validate all fields here
                if(dialogMode.equals(DialogMode.NEW_CLONE)){
                    //validate effective date
                    validateDatePicker(dialogRestEffectiveDate, selectedRestaurant.getDateEffective());
                }
                validateTeam(dialogRestLocation,locationChoice.getLocationTeamById(selectedRestaurant.getTeamId()));
                validateIntegerField(dialogRestCommission, getDoubleAsInteger(selectedRestaurant.getCommissionRate()));
                validateIntegerField(dialogRestCommissionPhonein, getDoubleAsInteger(selectedRestaurant.getCommissionRatePhonein()));
                validateField(dialogRestCommPerDelivery,selectedRestaurant.getCommissionPerDelivery());
                validateField(dialogRestCommPerPhonin,selectedRestaurant.getCommissionPerPhonein());
                validateField(dialogRestDelFeeFromVendor,selectedRestaurant.getDeliveryFeeFromVendor());
                validateIntegerField(dialogRestStartDayOffset, selectedRestaurant.getStartDayOffset());
                validateCheckbox(dialogRestPOSGlobal,selectedRestaurant.getPosGlobal());
                validateCheckbox(dialogRestPOSPhonein,selectedRestaurant.getPosPhonein());
                validateCheckbox(dialogRestActiveForPayout,selectedRestaurant.getActiveForPayout());
                validateCheckbox(dialogRestInvoicedVendor,selectedRestaurant.getUseInvoiceProcessing());
                validateCheckbox(dialogRestProcessOrderText,selectedRestaurant.getProcessOrderText());
                validateTextField(dialogRestGlobalAuthCode, selectedRestaurant.getGlobalAuthCode());
                validateIntegerField(dialogRestFormId, Math.toIntExact(selectedRestaurant.getFormId()));

                validateEmailField(dialogRestEmailEditor.getList(), dialogRestEmailEditor.getValue(), selectedRestaurant.getEmail());
            }
        }
        //log.info("dialogValidate: hasChangedValues:" + hasChangedValues + " email:" + hasChangedValuesEmail + " other:" + hasChangedValuesOther);
        if(hasChangedValues){
            enableOkReset(true);
            if(hasChangedValuesEmail){
                tabEmailIcon.setColor(UIUtilities.iconColorHighlighted);
            }else{
                tabEmailIcon.setColor(UIUtilities.iconColorNotHighlighted);
            }
            if(hasChangedValuesOther){
                tabOtherIcon.setColor(UIUtilities.iconColorHighlighted);
            }else{
                tabOtherIcon.setColor(UIUtilities.iconColorNotHighlighted);
            }
        }else{
            enableOkReset(false);
            tabEmailIcon.setColor(UIUtilities.iconColorNotHighlighted);
            tabOtherIcon.setColor(UIUtilities.iconColorNotHighlighted);
        }
    }

    private void validateField(NumberField field, Double value){
        if(value==null && field.getValue()==null){
            field.getStyle().set("box-shadow","none");
        }else if(value==null && field.getValue()!=null){
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }else if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }
    }

    private void validateTeam(Select<Team> field, Team value){
        log.info("validateTeam: value:" + value + " field value:" + field.getValue());
        log.info("validateTeam: notFound:" + (locationChoice.isTeamNotFound(value)) );
        log.info("validateTeam: field id:" + field.getValue() + " value id:" + value.getTeamId());
        if((field.getValue()==null)||(locationChoice.isTeamNotFound(field.getValue()))||(field.getValue().getTeamId().equals(value.getTeamId()))){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }
    }

    private void validateCheckbox(Checkbox field, Boolean value){
        if(field.getValue().equals(value) || (value==null && field.getValue().equals(Boolean.FALSE))){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }
    }

    private void validateDatePicker(DatePicker field, LocalDate value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }

    private void validateIntegerField(IntegerField field, Integer value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }
    }

    private void validateTextField(TextField field, String value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesOther = Boolean.TRUE;
        }
    }

    private void validateEmailField(ListBox field, String fieldValue, String value){
        if(fieldValue.equals(value) || (value==null && field.isEmpty()) || (fieldValue==null && value.isEmpty())){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
            hasChangedValuesEmail = Boolean.TRUE;
        }
    }

    private void dialogSave() {
        log.info("dialogSave: called for:" + selectedRestaurant.toString());
        if(dialogRestLocation.getValue()==null){
            selectedRestaurant.setTeamId(null);
        }else{
            selectedRestaurant.setTeamId(dialogRestLocation.getValue().getTeamId());
        }

        if(dialogRestCommission.getValue()==null || dialogRestCommission.getValue().equals(0)){
            selectedRestaurant.setCommissionRate(0.0);
        }else{
            selectedRestaurant.setCommissionRate(getIntegerAsDouble(dialogRestCommission.getValue()));
        }
        if(dialogRestCommissionPhonein.getValue()==null || dialogRestCommissionPhonein.getValue().equals(0)){
            selectedRestaurant.setCommissionRatePhonein(0.0);
        }else{
            selectedRestaurant.setCommissionRatePhonein(getIntegerAsDouble(dialogRestCommissionPhonein.getValue()));
        }
        if(dialogRestCommPerDelivery.getValue()==null){
            selectedRestaurant.setCommissionPerDelivery(0.0);
        }else{
            selectedRestaurant.setCommissionPerDelivery(dialogRestCommPerDelivery.getValue());
        }
        if(dialogRestCommPerPhonin.getValue()==null){
            selectedRestaurant.setCommissionPerPhonein(0.0);
        }else{
            selectedRestaurant.setCommissionPerPhonein(dialogRestCommPerPhonin.getValue());
        }
        if(dialogRestDelFeeFromVendor.getValue()==null){
            selectedRestaurant.setDeliveryFeeFromVendor(0.0);
        }else{
            selectedRestaurant.setDeliveryFeeFromVendor(dialogRestDelFeeFromVendor.getValue());
        }
        if(dialogRestStartDayOffset.getValue()==null){
            selectedRestaurant.setStartDayOffset(0);
        }else{
            selectedRestaurant.setStartDayOffset(dialogRestStartDayOffset.getValue());
        }
        selectedRestaurant.setPosGlobal(dialogRestPOSGlobal.getValue());
        selectedRestaurant.setPosPhonein(dialogRestPOSPhonein.getValue());
        selectedRestaurant.setActiveForPayout(dialogRestActiveForPayout.getValue());
        selectedRestaurant.setUseInvoiceProcessing(dialogRestInvoicedVendor.getValue());
        selectedRestaurant.setProcessOrderText(dialogRestProcessOrderText.getValue());
        if(dialogRestGlobalAuthCode.getValue()==null){
            selectedRestaurant.setGlobalAuthCode("");
        }else{
            selectedRestaurant.setGlobalAuthCode(dialogRestGlobalAuthCode.getValue());
        }
        if(dialogRestFormId.getValue()==null){
            selectedRestaurant.setFormId(0L);
        }else{
            selectedRestaurant.setFormId(Long.valueOf(dialogRestFormId.getValue()));
        }


        selectedRestaurant.setEmail(dialogRestEmailEditor.getValue());

        if(dialogMode.equals(DialogMode.NEW_CLONE)){
            selectedRestaurant.setDateEffective(dialogRestEffectiveDate.getValue());
            originalRestaurantThatWasCloned.setDateExpired(selectedRestaurant.getDateEffective().minusDays(1));
            log.info("dialogSave: clone   :" + selectedRestaurant);
            log.info("dialogSave: original:" + originalRestaurantThatWasCloned);
            restaurantRepository.save(originalRestaurantThatWasCloned);
        }

        restaurantRepository.save(selectedRestaurant);
        UIUtilities.showNotification("Updated");

        //refresh
        refreshGrid();
        restDialog.close();
    }

    private Integer getDoubleAsInteger(Double value){
        Integer newInt = 0;
        if(value==null) return newInt;
        Long newValue = Math.round(value * 100);
        return Math.toIntExact(newValue);
    }

    private Double getIntegerAsDouble(Integer value){
        Double newDouble = 0.0;
        if(value==null) return newDouble;
        newDouble = value * 0.01;
        return newDouble;
    }

    @Override
    public void locationChanged() {
        refreshGrid();
    }

}
