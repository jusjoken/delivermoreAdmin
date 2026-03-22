package ca.admin.delivermore.views.utility;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePickerVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.components.custom.ButtonTextField;
import ca.admin.delivermore.data.entity.GiftCardEntity;
import ca.admin.delivermore.data.entity.GiftCardTranactionEntity;
import ca.admin.delivermore.data.scheduler.SchedulerEventDialog;
import ca.admin.delivermore.data.service.GiftCardRepository;
import ca.admin.delivermore.data.service.GiftCardTranactionRepository;
import ca.admin.delivermore.gridexporter.ButtonsAlignment;
import ca.admin.delivermore.gridexporter.GridExporter;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Gift Cards")
@Route(value = "giftcards", layout = MainLayout.class)
@RolesAllowed("MANAGER")
public class GiftCardView extends VerticalLayout {

    enum DialogMode{
        NEW, EDIT, DELETE
    }

    private Logger log = LoggerFactory.getLogger(GiftCardView.class);
    private VerticalLayout mainLayout = UIUtilities.getVerticalLayout();
    private EmailService emailService;

    //Dialog fields
    private Dialog gcDialog = new Dialog();
    private DialogMode dialogMode = DialogMode.EDIT;

    private Button dialogOkButton = new Button("OK");
    private Icon okIcon = new Icon("lumo", "checkmark");
    private Icon resetIcon = new Icon("lumo", "undo");

    private Button dialogResetButton = new Button("Reset");
    private Button dialogCancelButton = new Button("Cancel");
    private Button dialogCloseButton = new Button(new Icon("lumo", "cross"));

    private ButtonTextField dialogCode = new ButtonTextField("Gift Card Code");
    private DatePicker dialogIssueDate = new DatePicker("Issue Date");
    private NumberField dialogAmount = UIUtilities.getNumberField("Amount", false, "$");
    private NumberField dialogRedeemed = UIUtilities.getNumberField("Redeemed", false, "$");
    private TextField dialogCustomerName = new TextField("Customer Name");
    private TextField dialogNotes = new TextField("Notes");
    private EmailField dialogEmail = new EmailField("Email");

    private Checkbox dialogAsGift = new Checkbox("Send as gift");

    private TextField dialogGiftName = new TextField("Recipient Name");

    private EmailField dialogGiftEmail = new EmailField("Recipient Email");

    private TextField dialogGiftNote = new TextField("Recipient Note");

    private Boolean validationEnabled = Boolean.FALSE;
    private Boolean hasChangedValues = Boolean.FALSE;

    Grid<GiftCardEntity> grid = new Grid<>();
    private GiftCardEntity selectedGiftCard = new GiftCardEntity();

    GiftCardRepository giftCardRepository;
    GiftCardTranactionRepository giftCardTranactionRepository;

    private List<GiftCardEntity> giftCardList;

    public GiftCardView(GiftCardRepository giftCardRepository, GiftCardTranactionRepository giftCardTranactionRepository, EmailService emailService) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardTranactionRepository = giftCardTranactionRepository;
        this.emailService = emailService;
        dialogConfigure();

        mainLayout.add(getToolbar());
        mainLayout.add(getGrid());
        setSizeFull();
        mainLayout.setSizeFull();
        add(mainLayout);

        // layout configuration
        //setSizeFull();
        //setSizeUndefined();
        //add(getToolbar());
        //add(getGrid());

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
            dialogOpen(new GiftCardEntity());
        });

        toolbar.add(addNew,refreshButton);
        return toolbar;
    }

    private VerticalLayout getGrid(){
        VerticalLayout gridLayout = UIUtilities.getVerticalLayout();
        gridLayout.setWidthFull();
        gridLayout.setHeightFull();
        //gridLayout.setSizeUndefined();

        refreshGrid();
        grid.setSizeFull();
        //grid.setWidthFull();
        //grid.setWidthFull();
        //grid.setHeight("400px");
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setMultiSort(true, Grid.MultiSortPriority.APPEND);
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        GridExporter<GiftCardEntity> exporter = GridExporter.createFor(grid);
        Grid.Column editIconColumn = grid.addComponentColumn(item -> {
            Icon editIcon = new Icon("lumo", "edit");
            editIcon.setTooltipText("Edit gift card");
            //Button editButton = new Button("Edit");
            editIcon.addClickListener(e -> {
                dialogMode = DialogMode.EDIT;

                /*
                String jsonString = "";
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                objectMapper.findAndRegisterModules();
                try {
                    jsonString = objectMapper.writeValueAsString(item);
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
                log.info("***JSON:" + jsonString);

                 */

                dialogOpen(item);
            });
            return editIcon;
        }).setWidth("40px").setFlexGrow(0).setFrozen(false);
        exporter.setExportColumn(editIconColumn,false);

        Grid.Column emailIconColumn = grid.addComponentColumn(item -> {
            Icon emailIcon = new Icon("vaadin", "envelope-o");
            emailIcon.setTooltipText("Email gift card");
            emailIcon.addClickListener(e -> {
                confirmEmailGiftCard(item);
            });
            return emailIcon;
        }).setWidth("40px").setFlexGrow(0).setFrozen(false);
        exporter.setExportColumn(emailIconColumn,false);

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

        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getIssued).setFlexGrow(0).setSortable(true),true,"Issued");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getCode).setFlexGrow(0).setSortable(true),true,"Code");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getAmount).setFlexGrow(0).setSortable(false),true,"Amount");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getRedeemed).setFlexGrow(0).setSortable(false),true,"Redeemed");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getBalance).setFlexGrow(0).setSortable(false),true,"Balance");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getCustomerName).setFlexGrow(0).setSortable(true),true,"Customer Name");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getAsGift).setFlexGrow(0).setSortable(true),true,"As Gift");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getNotes).setFlexGrow(0).setSortable(false),true,"Notes");
        exporter.createExportColumn(grid.addColumn(GiftCardEntity::getEmailAddresses).setWidth("150px").setFlexGrow(1).setSortable(false),true,"Send to Email");

        exporter.setFileName("GiftCardExport" + new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime()));
        exporter.setButtonsAlignment(ButtonsAlignment.LEFT);

        grid.setItemDetailsRenderer(new ComponentRenderer<>(item -> {
            VerticalLayout div = new VerticalLayout();
            List<GiftCardTranactionEntity> list = item.getTransactions();
            //log.info("ItemDetailsRenderer: size" + list.size());

            if (!list.isEmpty()) {
                Grid<GiftCardTranactionEntity> details = new Grid<>();
                GridExporter<GiftCardTranactionEntity> detailsExporter = GridExporter.createFor(details);

                details.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
                details.addThemeVariants(GridVariant.LUMO_COMPACT);
                details.setItems(list);
                details.setAllRowsVisible(true);
                div.setSizeFull();
                div.add(details);
                Grid.Column detailsEditIconColumn = details.addComponentColumn(detailItem -> {
                    Icon editIcon = new Icon("lumo", "edit");
                    editIcon.setTooltipText("Edit transaction amount");
                    editIcon.addClickListener(e -> {
                        editGiftCardTransaction(detailItem);
                    });
                    return editIcon;
                }).setWidth("50px").setFlexGrow(0);
                detailsExporter.setExportColumn(detailsEditIconColumn,false);

                Grid.Column detailsDeleteIconColumn = details.addComponentColumn(detailsItem -> {
                    Icon detailsDeleteIcon = new Icon("lumo", "cross");
                    detailsDeleteIcon.setTooltipText("Delete transaction");
                    detailsDeleteIcon.setColor("red");
                    detailsDeleteIcon.addClickListener(e -> {
                        confirmDetailsDelete(detailsItem);
                    });
                    return detailsDeleteIcon;
                }).setWidth("50px").setFlexGrow(0);
                detailsExporter.setExportColumn(detailsDeleteIconColumn,false);
                detailsExporter.createExportColumn(details.addColumn(new LocalDateTimeRenderer<>(GiftCardTranactionEntity::getTransactionDateTime,"MM-dd HH:mm")).setFlexGrow(1).setSortable(true),true,"Redeemed Date/Time");
                detailsExporter.createExportColumn(details.addColumn(GiftCardTranactionEntity::getAmount),true,"Redeemed Amount");
                detailsExporter.createExportColumn(details.addColumn(GiftCardTranactionEntity::getUserName),true,"Driver");

                detailsExporter.setFileName("GiftCardTransactioinsExport" + new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime()));
                detailsExporter.setButtonsAlignment(ButtonsAlignment.LEFT);

            }else{
                NativeLabel noTransactions = new NativeLabel("There are no transactions for this Gift Card.");
                div.setSizeFull();
                div.add(noTransactions);
            }

            return div;
        }));

        gridLayout.add(grid);
        gridLayout.setFlexGrow(1,grid);

        return gridLayout;
    }

    private void confirmEmailGiftCard(GiftCardEntity item) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Email Gift Card?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Send");
        //dialog.setConfirmButtonTheme("error primary");
        dialog.setText(
                "Send Gift Card " + item.getCode() + " for $" + item.getAmount() + " to " + item.getCustomerName() + " (" + item.getEmailAddresses() + ")?");
        dialog.addConfirmListener(event -> {
            sendGiftCardEmail(item);
        });
        dialog.open();
    }

    private void sendGiftCardEmail(GiftCardEntity item) {
        //TODO: send email
        //send email to customer and support with amount and balance
        emailService.sendMailWithHtmlBody(item.getEmailFullAddress(),item.getEmailSubject(), item.getEmailBody());

        UIUtilities.showNotification("Gift Card details sent");
    }

    private void editGiftCardTransaction(GiftCardTranactionEntity detailItem) {
        Dialog dialog = new Dialog();

        dialog.setHeaderTitle("Edit transaction amount");

        VerticalLayout dialogLayout = UIUtilities.getVerticalLayout();
        dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        dialogLayout.getStyle().set("width", "18rem").set("max-width", "100%");

        NumberField transactionAmount = UIUtilities.getNumberField(false);
        transactionAmount.setLabel("Amount");
        transactionAmount.setValue(detailItem.getAmount());
        transactionAmount.setAutofocus(true);
        transactionAmount.setAutoselect(true);
        dialogLayout.add(transactionAmount);

        dialog.add(dialogLayout);

        Button saveButton = new Button("Update");
        saveButton.addClickListener(e -> {
            detailItem.setAmount(transactionAmount.getValue());
            giftCardTranactionRepository.save(detailItem);
            dialog.close();
            refreshGrid();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton);
        dialog.getFooter().add(saveButton);
        dialog.open();

    }

    private void refreshGrid() {
        //TODO: add filter to only show gc with balance
        giftCardList = giftCardRepository.findAllOrderByIssuedDesc();
        log.info("GiftCardView: refreshGrid: count:" + giftCardList.size());
        grid.setItems(giftCardList);
        grid.getDataProvider().refreshAll();
    }

    private void confirmDetailsDelete(GiftCardTranactionEntity detailsItem) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Gift Card Transaction?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.setText(
                "Delete transaction dated " + detailsItem.getTransactionDateTimeFmt() + " for $" + detailsItem.getAmount() + "?");
        dialog.addConfirmListener(event -> {
            giftCardTranactionRepository.delete(detailsItem);
            refreshGrid();
        });
        dialog.open();
    }

    private void confirmDelete(GiftCardEntity giftCard){
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Gift Card?");
        List<GiftCardTranactionEntity> tranactionEntities = giftCard.getTransactions();
        if(tranactionEntities.size()>0){
            dialog.setText(
                    "This card has " + tranactionEntities.size() + " transactions. Delete gift card with code " + giftCard.getCode() + "?");
        }else{
            dialog.setText(
                    "Delete gift card with code " + giftCard.getCode() + "?");
        }

        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            if(tranactionEntities.size()>0){
                for (GiftCardTranactionEntity transaction: tranactionEntities) {
                    giftCardTranactionRepository.delete(transaction);
                }
            }
            giftCardRepository.delete(giftCard);
            refreshGrid();
        });
        dialog.open();
    }

    public void dialogConfigure() {
        gcDialog.getElement().setAttribute("aria-label", "Edit gift card details");

        VerticalLayout dialogLayout = dialogLayout();
        gcDialog.add(dialogLayout);
        gcDialog.setHeaderTitle("Edit Selected Gift Card");

        dialogCloseButton.addClickListener((e) -> gcDialog.close());
        dialogCloseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        gcDialog.getHeader().add(dialogCloseButton);
        gcDialog.setCloseOnEsc(true);
        dialogCancelButton.addClickListener((e) -> gcDialog.close());

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

        gcDialog.getFooter().add(footerLayout);
    }

    private void dialogSave() {
        log.info("dialogSave: called for:" + selectedGiftCard.toString());
        if(dialogAmount.getValue()==null){
            selectedGiftCard.setAmount(0.0);
        }else{
            selectedGiftCard.setAmount(dialogAmount.getValue());
        }
        selectedGiftCard.setCode(dialogCode.getValue());
        selectedGiftCard.setCustomerName(dialogCustomerName.getValue());
        selectedGiftCard.setCustomerEmail(dialogEmail.getValue());
        selectedGiftCard.setNotes(dialogNotes.getValue());
        selectedGiftCard.setIssued(dialogIssueDate.getValue());
        selectedGiftCard.setAsGift(dialogAsGift.getValue());
        selectedGiftCard.setGiftName(dialogGiftName.getValue());
        selectedGiftCard.setGiftEmail(dialogGiftEmail.getValue());
        selectedGiftCard.setGiftNote(dialogGiftNote.getValue());

        giftCardRepository.save(selectedGiftCard);
        UIUtilities.showNotification("Updated");

        //refresh
        refreshGrid();
        gcDialog.close();

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
        dialogCode.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogCode.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        dialogCode.setButtonIcon(new Icon("lumo", "reload"));
        dialogCode.getTextField().setWidth("120px");
        dialogCode.addClickListener(e -> {
            dialogCode.setValue(selectedGiftCard.getUniqueCode());
        });
        dialogIssueDate.setReadOnly(false);
        dialogIssueDate.addThemeVariants(DatePickerVariant.LUMO_SMALL);
        dialogIssueDate.setWidth("150px");
        dialogIssueDate.addValueChangeListener(e -> {
            if(validationEnabled){
                if(e.getValue()==null) dialogIssueDate.setValue(e.getOldValue());
                dialogValidate();
            }
        });
        HorizontalLayout codeAndIssuedLayout = UIUtilities.getHorizontalLayout();
        codeAndIssuedLayout.add(dialogCode,dialogIssueDate);

        dialogAmount.setReadOnly(false);
        dialogAmount.setMin(0.01);
        dialogAmount.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogAmount.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogRedeemed.setReadOnly(true);
        dialogRedeemed.addThemeVariants(TextFieldVariant.LUMO_SMALL);

        HorizontalLayout amountAndRedeemedLayout = UIUtilities.getHorizontalLayout();
        amountAndRedeemedLayout.add(dialogAmount,dialogRedeemed);

        dialogCustomerName.setReadOnly(false);
        dialogCustomerName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogCustomerName.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogEmail.setReadOnly(false);
        dialogEmail.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogEmail.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogNotes.setReadOnly(false);
        dialogNotes.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogNotes.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        VerticalLayout fieldLayout = new VerticalLayout(codeAndIssuedLayout,amountAndRedeemedLayout,dialogCustomerName,dialogEmail,dialogNotes);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.getStyle().set("width", "300px").set("max-width", "100%");

        dialogAsGift.addValueChangeListener(e -> {
            showHideGiftFields(e.getValue());
            if(validationEnabled) dialogValidate();
        });

        dialogGiftName.setReadOnly(false);
        dialogGiftName.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogGiftName.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogGiftEmail.setReadOnly(false);
        dialogGiftEmail.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogGiftEmail.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });

        dialogGiftNote.setReadOnly(false);
        dialogGiftNote.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        dialogGiftNote.addValueChangeListener(e -> {
            if(validationEnabled) dialogValidate();
        });
        fieldLayout.add(dialogAsGift,dialogGiftName,dialogGiftEmail,dialogGiftNote);
        return fieldLayout;
    }

    private void showHideGiftFields(Boolean value) {
        if(value){
            dialogGiftName.setVisible(true);
            dialogGiftEmail.setVisible(true);
            dialogGiftNote.setVisible(true);
        }else{
            dialogGiftName.setVisible(false);
            dialogGiftEmail.setVisible(false);
            dialogGiftNote.setVisible(false);
        }
    }

    private void dialogOpen(GiftCardEntity giftCard){
        selectedGiftCard = giftCard;
        if(dialogMode.equals(DialogMode.NEW)){
            dialogCode.setReadOnly(false);
        }else{
            dialogCode.setReadOnly(true);
        }
        setValues();
        validationEnabled = Boolean.TRUE;

        dialogValidate();
        gcDialog.open();
    }

    private void setValues(){
        //set values
        if(dialogMode.equals(DialogMode.NEW)){
            dialogCode.setValue(selectedGiftCard.getUniqueCode());
        }else{
            dialogCode.setValue(selectedGiftCard.getCode());
        }
        if(selectedGiftCard.getIssued()==null){
            dialogIssueDate.setValue(LocalDate.now());
        }else{
            dialogIssueDate.setValue(selectedGiftCard.getIssued());
        }
        if(selectedGiftCard.getAsGift()==null){
            dialogAsGift.setValue(Boolean.FALSE);
            showHideGiftFields(Boolean.FALSE);
        }else{
            dialogAsGift.setValue(selectedGiftCard.getAsGift());
            showHideGiftFields(selectedGiftCard.getAsGift());
        }
        if(selectedGiftCard.getCustomerEmail()==null){
            dialogEmail.setValue("");
        }else{
            dialogEmail.setValue(selectedGiftCard.getCustomerEmail());
        }
        if(selectedGiftCard.getCustomerName()==null){
            dialogCustomerName.setValue("");
        }else{
            dialogCustomerName.setValue(selectedGiftCard.getCustomerName());
        }
        if(selectedGiftCard.getNotes()==null){
            dialogNotes.setValue("");
        }else{
            dialogNotes.setValue(selectedGiftCard.getNotes());
        }
        if(selectedGiftCard.getGiftEmail()==null){
            dialogGiftEmail.setValue("");
        }else{
            dialogGiftEmail.setValue(selectedGiftCard.getGiftEmail());
        }
        if(selectedGiftCard.getGiftName()==null){
            dialogGiftName.setValue("");
        }else{
            dialogGiftName.setValue(selectedGiftCard.getGiftName());
        }
        if(selectedGiftCard.getGiftNote()==null){
            dialogGiftNote.setValue("");
        }else{
            dialogGiftNote.setValue(selectedGiftCard.getGiftNote());
        }

        if(selectedGiftCard.getAmount()==null){
            dialogAmount.setValue(0.0);
        }else{
            dialogAmount.setValue(selectedGiftCard.getAmount());
        }
        if(selectedGiftCard.getRedeemed()==null){
            dialogRedeemed.setValue(0.0);
        }else{
            dialogRedeemed.setValue(selectedGiftCard.getRedeemed());
        }

    }

    private void dialogValidate() {
        if(validationEnabled && selectedGiftCard!=null){
            if(dialogMode.equals(SchedulerEventDialog.DialogMode.NEW)){
                hasChangedValues = Boolean.TRUE;
            }else{
                hasChangedValues = Boolean.FALSE;
                //validate all fields here
                validateField(dialogAmount,selectedGiftCard.getAmount());

                validateDatePicker(dialogIssueDate,selectedGiftCard.getIssued());

                validateTextField(dialogCustomerName, selectedGiftCard.getCustomerName());
                validateTextField(dialogNotes, selectedGiftCard.getNotes());
                validateEmailField(dialogEmail, selectedGiftCard.getCustomerEmail());

                validateCheckbox(dialogAsGift, selectedGiftCard.getAsGift());
                validateTextField(dialogGiftName, selectedGiftCard.getGiftName());
                validateTextField(dialogGiftNote, selectedGiftCard.getGiftNote());
                validateEmailField(dialogGiftEmail, selectedGiftCard.getGiftEmail());

            }
        }
        //log.info("dialogValidate: hasChangedValues:" + hasChangedValues + " email:" + hasChangedValuesEmail + " other:" + hasChangedValuesOther);
        if(hasChangedValues){
            enableOkReset(true);
        }else{
            enableOkReset(false);
        }
    }

    private void validateCheckbox(Checkbox field, Boolean value){
        if(value==null && field.getValue().equals(Boolean.FALSE) || field.getValue().equals(value)){
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
    private void validateDatePicker(DatePicker field, LocalDate value){
        if(field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
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
    private void validateEmailField(EmailField field, String value){
        log.info("validateEmailField: fieldValue:" + field.getValue() + " value:" + value + " isBlank:" + field.getValue().isBlank() + " isEmpty:" + field.getValue().isEmpty() + " emptyValue:" + field.getEmptyValue());
        if(value==null && field.getValue().isEmpty() || field.getValue().equals(value)){
            field.getStyle().set("box-shadow","none");
        }else{
            field.getStyle().set("box-shadow",UIUtilities.boxShadowStyle);
            field.getStyle().set("border-radius",UIUtilities.boxShadowStyleRadius);
            hasChangedValues = Boolean.TRUE;
        }
    }


}
