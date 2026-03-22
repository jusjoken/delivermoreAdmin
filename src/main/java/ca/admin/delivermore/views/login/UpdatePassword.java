package ca.admin.delivermore.views.login;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.Rule;
import org.passay.RuleResult;
import org.passay.WhitespaceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import ca.admin.delivermore.security.AuthenticatedUser;
import ca.admin.delivermore.views.UIUtilities;
import ca.admin.delivermore.views.home.HomeView;

@PageTitle("Update Password")
@Route(value = "updatepassword")
@AnonymousAllowed
public class UpdatePassword extends VerticalLayout implements HasUrlParameter<String>{
    private static final Logger log = LoggerFactory.getLogger(UpdatePassword.class);
    private final AuthenticatedUser authenticatedUser;

    private String errorMessage = "";
    private Icon checkIconValid;
    private Icon checkIconMatch;
    private Span passwordValidText;
    private Span passwordMatchText;

    public UpdatePassword(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    private void verifyAndDisplay(String tokenParam){
        //verify first
        if(tokenParam==null){
            log.info("verifyAndDisplay: null token passed");
            showMessageDialog(false);
            //UI.getCurrent().navigate(HomeView.class);
            //getUI().ifPresent(ui -> ui.navigate(HomeView.class));
        }else{
            String validateTokenResponse = authenticatedUser.validatePasswordResetToken(tokenParam);
            log.info("verifyAndDisplay: validateTokenResponse:" + validateTokenResponse);
            if(validateTokenResponse!=null){
                log.info("verifyAndDisplay: invalid token passed:" + tokenParam);
                //getUI().ifPresent(ui -> ui.navigate(HomeView.class));
                //UI.getCurrent().navigate(HomeView.class);
                showMessageDialog(false);
            }else{
                log.info("verifyAndDisplay: valid token passed:" + tokenParam);
                setSpacing(false);

            /*
            Image img = new Image("images/delivermorelogo.png", "DeliverMore Admin");
            img.setWidth("400px");
            add(img);

            add(new H2("Update Password"));
            add(new Paragraph("Enter and confirm new password below"));

             */

                add(UIUtilities.createStandardHeader("Update Password", "Enter and confirm new password below"));

                PasswordField password = new PasswordField("New password");
                PasswordField passwordConfirm = new PasswordField("Verify password");
                password.setAutofocus(true);

                checkIconValid = VaadinIcon.CHECK.create();
                checkIconValid.setVisible(false);
                password.setSuffixComponent(checkIconValid);

                Div passwordValid = new Div();
                passwordValidText = new Span();
                passwordValid.add(new Text("Password: "),
                        passwordValidText);
                password.setHelperComponent(passwordValid);
                password.getElement().setAttribute("name", "password");
                password.setClearButtonVisible(true);
                password.setValueChangeMode(ValueChangeMode.EAGER);
                password.addValueChangeListener(e -> {
                    updateHelperValid(e.getValue());
                    updateHelperMatch(e.getValue(), passwordConfirm.getValue());
                });

                checkIconMatch = VaadinIcon.CHECK.create();
                checkIconMatch.setVisible(false);
                passwordConfirm.setSuffixComponent(checkIconMatch);
                Div passwordMatch = new Div();
                passwordMatchText = new Span();
                passwordMatch.add(new Text("Verify: "),
                        passwordMatchText);
                passwordConfirm.setHelperComponent(passwordMatch);
                passwordConfirm.setValueChangeMode(ValueChangeMode.EAGER);
                passwordConfirm.addValueChangeListener(e -> {
                    updateHelperMatch(password.getValue(), e.getValue());
                });

                passwordConfirm.setClearButtonVisible(true);

                Button updatePasswordButton = new Button("Update password");
                updatePasswordButton.setEnabled(true);
                updatePasswordButton.setDisableOnClick(true);
                updatePasswordButton.addClickListener(e -> {
                    //update the password
                    if(validatePasswords(password.getValue(),passwordConfirm.getValue())){
                        if(authenticatedUser.savePassword(password.getValue(),tokenParam)){
                            showMessageDialog(true);
                        }else{
                            showMessageDialog(false);
                        }
                    }else{
                        updatePasswordButton.setEnabled(true);
                    }
                });

                add(password,passwordConfirm,updatePasswordButton);

                String passwordNote = "<p>Note: Password must be 8 or more characters in length. Password must contain 1 or more uppercase characters, 1 or more lowercase characters, 1 or more numbers and 1 or more special characters.</p>";
                Span note = new Span();
                note.getElement().setProperty("innerHTML", passwordNote);
                note.getStyle().set("font-size", "11px");
                note.setWidth("300px");
                add(note);

                setSizeFull();
                setJustifyContentMode(JustifyContentMode.CENTER);
                setDefaultHorizontalComponentAlignment(Alignment.CENTER);
                getStyle().set("text-align", "center");

            }
        }

    }

    private Boolean validatePasswords(String pass1, String pass2){
        if(pass1==null || pass1.isEmpty() || pass2==null || pass2.isEmpty()){
            UIUtilities.showNotification("Password cannot be blank.");
            return false;
        }

        if(!pass1.equals(pass2)){
            UIUtilities.showNotification("Passwords do not match.");
            return false;
        }

        errorMessage = "";
        if(validatePasswordRules(pass1)){
            log.info("validatePasswords: password validated");
            return true;
        }else{
            log.info("validatePasswords: Invalid password:" + errorMessage);
            UIUtilities.showNotification("Invalid password:" + errorMessage);
            return false;
        }

    }

    private Boolean validatePasswordRules(String pass){
        if(pass==null || pass.isEmpty()){
            errorMessage = "Password cannot be blank.";
            return false;
        }

        List<Rule> rules = new ArrayList<>();
        //Rule 1: Password length should be in between
        //8 and 128 characters
        rules.add(new LengthRule(8, 128));
        //Rule 2: No whitespace allowed
        rules.add(new WhitespaceRule());
        //Rule 3.a: At least one Upper-case character
        rules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
        //Rule 3.b: At least one Lower-case character
        rules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
        //Rule 3.c: At least one digit
        rules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
        //Rule 3.d: At least one special character
        rules.add(new CharacterRule(EnglishCharacterData.Special, 1));

        PasswordValidator validator = new PasswordValidator(rules);
        PasswordData password = new PasswordData(pass);
        RuleResult result = validator.validate(password);

        if(result.isValid()){
            log.info("validatePasswords: password validated");
            errorMessage = "";
            return true;
        }else{
            log.info("validatePasswords: Invalid password:" + validator.getMessages(result));
            errorMessage = "Invalid password:" + validator.getMessages(result);
            return false;
        }

    }

    private void updateHelperValid(String password) {
        if(validatePasswordRules(password)){
            passwordValidText.setText("valid");
            passwordValidText.getStyle().set("color",
                    "var(--lumo-success-color)");
            checkIconValid.setVisible(true);
        }else{
            passwordValidText.setText("invalid");
            passwordValidText.getStyle().set("color",
                    "var(--lumo-error-color)");
            checkIconValid.setVisible(false);
        }
    }

    private void updateHelperMatch(String pass1, String pass2) {
        if(pass1==null || pass1.isEmpty() || pass2==null || pass2.isEmpty()){
            passwordMatchText.setText("cannot be blank");
            passwordMatchText.getStyle().set("color",
                    "var(--lumo-error-color)");
            checkIconMatch.setVisible(false);
        }else if(pass1.equals(pass2)){
            passwordMatchText.setText("match");
            passwordMatchText.getStyle().set("color",
                    "var(--lumo-success-color)");
            checkIconMatch.setVisible(true);
        }else{
            passwordMatchText.setText("passwords do not match");
            passwordMatchText.getStyle().set("color",
                    "var(--lumo-error-color)");
            checkIconMatch.setVisible(false);
        }
    }

    private void showMessageDialog(Boolean success){
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Update Password");
        dialog.setCloseOnEsc(false);
        dialog.addConfirmListener(e -> {
            if(success){
                if (authenticatedUser.get().isPresent()) {
                    // Already logged in
                    log.info("UpdatePassword: success: already logged in - logging out");
                    authenticatedUser.logout();
                    //getUI().ifPresent(ui -> ui.navigate(LoginView.class));
                }else{
                    log.info("UpdatePassword: success: navigate to home");
                    getUI().ifPresent(ui -> ui.navigate(LoginView.class));
                }
            }else{
                log.info("UpdatePassword: failed: navigate to home");
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            }
        });
        String message = "";
        if(success){
            message = "<p>Password updated<br><br>Please login again.</p>";
            dialog.setText(new Html(message));
        }else{
            message = "<p>Password update failed<br><br>Please try reset again.</p>";
            dialog.setText(new Html(message));
        }
        dialog.setConfirmText("OK");
        dialog.open();
    }


    @Override
    public void setParameter(BeforeEvent beforeEvent, @OptionalParameter String s) {
        log.info("UpdatePassword: setParameter: param:" + s);
        Location location = beforeEvent.getLocation();
        QueryParameters queryParameters = location.getQueryParameters();

        Map<String, List<String>> parametersMap = queryParameters
                .getParameters();

        verifyAndDisplay(getParamFromList(parametersMap,"token"));

    }
    private String getParamFromList(Map<String, List<String>> map, String item){
        log.info("getParamFromList:" + item + " from map:" + map.values());
        if(map.containsKey(item)){
            log.info("getParamFromList: found item:" + item);
            return map.get(item).get(0);
        }else{
            log.info("getParamFromList: returning null");
            return null;
        }
    }

}
