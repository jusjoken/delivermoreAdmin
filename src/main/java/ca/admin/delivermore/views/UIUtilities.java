package ca.admin.delivermore.views;

import ca.admin.delivermore.components.custom.ButtonNumberField;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.theme.lumo.LumoIcon;

public class UIUtilities {

    public static final String boxShadowStyle = "inset 0px 0px 3px 4px var(--lumo-success-color)";
    public static final String boxShadowStyleRadius = "6px";
    public static final String iconColorNotHighlighted = "var(--lumo-contrast-30pct)";
    public static final String iconColorNormal = "var(--lumo-contrast-50pct)";
    public static final String iconColorHighlighted = "var(--lumo-success-color)";

    public static Details getDetails(){
        Details details = new Details();
        details.setWidthFull();
        details.addThemeVariants(DetailsVariant.FILLED);
        return details;
    }

    public static String getNumberFormatted(Double input){
        return String.format("%.2f",input);
    }

    public static IntegerField getPercentageField(String label, Boolean readOnly){
        IntegerField integerField = new IntegerField();
        Div suffixDiv = new Div();
        suffixDiv.setText("%");
        integerField.setSuffixComponent(suffixDiv);
        if(!label.isEmpty()){
            integerField.setLabel(label);
        }
        integerField.setReadOnly(readOnly);
        integerField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return integerField;
    }

    public static IntegerField getIntegerField(String label, Boolean readOnly, Integer number){
        IntegerField integerField = new IntegerField();
        if(!label.isEmpty()){
            integerField.setLabel(label);
        }
        integerField.setReadOnly(readOnly);
        integerField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        integerField.setValue(number);
        return integerField;
    }

    public static NumberField getNumberField(String label, Double number){
        NumberField numberField = getNumberField(label);
        numberField.setValue(number);
        return numberField;
    }

    public static NumberField getNumberField(Boolean readOnly){
        return getNumberField("",readOnly,"$");
    }
    public static NumberField getNumberField(){
        return getNumberField("",Boolean.TRUE, "$");
    }
    public static NumberField getNumberField(String label){
        return getNumberField(label,Boolean.TRUE, "$");
    }

    public static NumberField getNumberField(String label, Boolean readOnly, String prefix){
        Div prefixDiv = new Div();
        prefixDiv.setText(prefix);
        NumberField numberField = new NumberField();
        if(!label.isEmpty()){
            numberField.setLabel(label);
        }
        numberField.setReadOnly(readOnly);
        numberField.setPrefixComponent(prefixDiv);
        numberField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return numberField;
    }

    public static ButtonNumberField getButtonNumberField(String label, Boolean readOnly, String prefix){
        Div prefixDiv = new Div();
        prefixDiv.setText(prefix);
        ButtonNumberField numberField = new ButtonNumberField();
        if(!label.isEmpty()){
            numberField.setLabel(label);
        }
        numberField.setReadOnly(readOnly);
        numberField.setPrefixComponent(prefixDiv);
        numberField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return numberField;
    }


    public static TextField getTextFieldRO(String label, String text, String width ){
        TextField textField = getTextFieldRO(label,text);
        textField.setWidth(width);
        return textField;
    }
    public static TextField getTextFieldRO(String label, String text){
        TextField textField = new TextField(label);
        textField.setReadOnly(true);
        textField.setValue(text);
        textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return textField;
    }

    public static TextField getTextField(String label){
        TextField textField = new TextField();
        if(!label.isEmpty()){
            textField.setLabel(label);
        }
        textField.setReadOnly(false);
        textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return textField;
    }

    public static HorizontalLayout getHorizontalLayout(){
        return getHorizontalLayout(false,false,false);
    }
    public static HorizontalLayout getHorizontalLayout(Boolean paddingEnabled, Boolean spacingEnabled, Boolean marginEnabled){
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setPadding(paddingEnabled);
        horizontalLayout.setSpacing(spacingEnabled);
        horizontalLayout.setMargin(marginEnabled);
        horizontalLayout.setWidthFull();
        return horizontalLayout;
    }

    public static HorizontalLayout getHorizontalLayoutNoWidthCentered(){
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setPadding(false);
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        return horizontalLayout;
    }

    public static VerticalLayout getVerticalLayout(){
        return getVerticalLayout(false,false,false);
    }
    public static VerticalLayout getVerticalLayout(Boolean paddingEnabled, Boolean spacingEnabled, Boolean marginEnabled){
        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.setPadding(paddingEnabled);
        verticalLayout.setSpacing(spacingEnabled);
        verticalLayout.setMargin(marginEnabled);
        verticalLayout.setWidthFull();
        return verticalLayout;
    }

    public static String singlePlural(int count, String singular, String plural)
    {
        return count==1 ? singular : plural;
    }

    public static TextField createSmallTextField(String label) {
        TextField textField = new TextField(label);
        textField.addValueChangeListener(event ->{
            //setTooltip(event.getSource().getValue(),event.getSource());
        });
        textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        return textField;
    }

    public static void scrollIntoView(Component component){
        component.getElement().executeJs(
                "$0.scrollIntoView({behavior: \"smooth\", block: \"end\", inline: \"nearest\"});", component.getElement());
    }

    public static Button createSmallButton(String text) {
        return createButton(text, ButtonVariant.LUMO_SMALL);
    }

    public static Button createButton(String text, ButtonVariant... variants) {
        Button button = new Button(text);
        button.addThemeVariants(variants);
        button.getElement().setAttribute("aria-label", text);
        return button;
    }

    public static void showNotification(String text) {
        Notification.show(text, 3000, Notification.Position.BOTTOM_CENTER);
    }

    public static void showNotificationError(String text) {
        Notification notification = new Notification();
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.setPosition(Notification.Position.BOTTOM_CENTER);

        Div textDiv = new Div(new Text(text));

        Button closeButton = new Button(LumoIcon.CROSS.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        closeButton.getElement().setAttribute("aria-label", "Close");
        closeButton.addClickListener(event -> {
            notification.close();
        });

        HorizontalLayout layout = new HorizontalLayout(textDiv, closeButton);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        notification.add(layout);
        notification.open();
    }

    public static VerticalLayout createStandardHeader(String header, String message){
        VerticalLayout verticalLayout = getVerticalLayout();
        verticalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        verticalLayout.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);
        verticalLayout.getStyle().set("text-align", "center");

        Image img = new Image("images/delivermorelogo.png", "DeliverMore Admin");
        img.setWidth("400px");
        verticalLayout.add(img);

        verticalLayout.add(new H2(header));
        verticalLayout.add(new Paragraph(message));

        return verticalLayout;
    }

    public static Icon createStatusIcon(Boolean isTrue) {
        Icon icon;
        if (isTrue) {
            icon = VaadinIcon.CHECK.create();
            icon.getElement().getThemeList().add("badge success");
        } else {
            icon = VaadinIcon.CLOSE_SMALL.create();
            icon.getElement().getThemeList().add("badge error");
        }
        icon.getStyle().set("padding", "var(--lumo-space-xs");
        return icon;
    }

    public static class MenuEntry{

        public enum MenuKeys {
            ADDUSER,ADDMANAGER,ADDADMIN,REMOVEUSER,REMOVEMANAGER,REMOVEADMIN,PAYOUTENABLE,PAYOUTDISABLE,ALLOWLOGIN,DISABLELOGIN,PASSWORDRESET,PLACEHOLDER
        }

        private MenuKeys name;
        private String displayName;
        private Boolean placeHolder = Boolean.FALSE;

        public MenuEntry() {
            this.name = MenuKeys.PLACEHOLDER;
            this.displayName = "Placeholder";
            this.placeHolder = Boolean.TRUE;
        }

        public MenuEntry(MenuKeys name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        public MenuKeys getName() {
            return name;
        }

        public void setName(MenuKeys name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Boolean getPlaceHolder() {
            return placeHolder;
        }

        public void setPlaceHolder(Boolean placeHolder) {
            this.placeHolder = placeHolder;
        }

        @Override
        public String toString() {
            return "MenuEntry{" +
                    "name=" + name +
                    ", displayName='" + displayName + '\'' +
                    ", placeHolder=" + placeHolder +
                    '}';
        }
    }

}
