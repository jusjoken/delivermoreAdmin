package ca.admin.delivermore.components.custom;

import ca.admin.delivermore.views.UIUtilities;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;

public class ButtonTextField extends CustomField<String> {

    private TextField textField = new TextField();
    private Button button = new Button();
    private HorizontalLayout layout = UIUtilities.getHorizontalLayout();

    public ButtonTextField() {
        super();
        layout.setAlignItems(FlexComponent.Alignment.BASELINE);
        layout.add(textField,button);
        add(layout);
    }

    public ButtonTextField(String label){
        super();
        layout.setAlignItems(FlexComponent.Alignment.BASELINE);
        layout.add(textField,button);
        textField.setLabel(label);
        add(layout);
    }

    @Override
    protected String generateModelValue() {
        return textField.getValue();
    }

    @Override
    protected void setPresentationValue(String s) {
        textField.setValue(s);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        ((com.vaadin.flow.component.AbstractField<?, ?>) this).setReadOnly(readOnly);
        // super.setReadOnly(readOnly);
        layout.removeAll();
        if(readOnly){
            textField.setReadOnly(true);
            layout.add(textField);
        }else{
            layout.setAlignItems(FlexComponent.Alignment.BASELINE);
            layout.add(textField,button);
        }
    }

    public void setButtonIcon(Component icon) {
        button.setIcon(icon);
    }

    public void addClickListener(ComponentEventListener listener) {
        button.addClickListener(listener);
    }

    public void addThemeVariants(TextFieldVariant textFieldVariant){
        textField.addThemeVariants(textFieldVariant);
    }

    public TextField getTextField() {
        return textField;
    }
}
