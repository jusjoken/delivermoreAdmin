package ca.admin.delivermore.views.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import ca.admin.delivermore.security.AuthenticatedUser;
import ca.admin.delivermore.views.UIUtilities;
import ca.admin.delivermore.views.home.HomeView;

@PageTitle("Reset Password")
@Route(value = "resetpassword")
@AnonymousAllowed
public class PasswordReset extends VerticalLayout {
    private Logger log = LoggerFactory.getLogger(PasswordReset.class);
    private final AuthenticatedUser authenticatedUser;

    public PasswordReset(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;

        setSpacing(false);

        add(UIUtilities.createStandardHeader("Reset Password","Enter valid email below"));

        EmailField emailField = new EmailField("Email");
        emailField.setAutofocus(true);
        emailField.getElement().setAttribute("name", "email");
        emailField.setErrorMessage("Enter a valid email address");
        emailField.setClearButtonVisible(true);
        /*
        emailField.addValueChangeListener(e -> {
            log.info("PasswordReset: value changed:" + e.getValue());
        });

         */

        Button resetButton = new Button("Reset password");
        resetButton.addClickListener(e -> {
            log.info("PasswordReset: rest password called");
            if(authenticatedUser.resetPassword(emailField.getValue())){
                log.info("PasswordReset: reset complete - inform user");
                showMessageDialog( emailField.getValue(), true);
                //TODO: add message response area to page or use notification - decision
            }else{
                log.info("PasswordReset: reset failed - invalid user");
                showMessageDialog( emailField.getValue(), false);
            }

        });
        add(emailField, resetButton);

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");

    }

    private void showMessageDialog(String email, Boolean success){
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Reset Password");
        dialog.setCloseOnEsc(false);
        dialog.addConfirmListener(e -> {
            if(success){
                if (authenticatedUser.get().isPresent()) {
                    // Already logged in
                    log.info("PasswordReset: already logged in - logging out");
                    authenticatedUser.logout();
                }else{
                    getUI().ifPresent(ui -> ui.navigate(HomeView.class));
                }
            }
        });
        String message = "";
        if(success){
            message = "<p>Password reset completed for user:<br>" + email + "<br>You have 24 hours to respond to the reset password email.</p>";
            dialog.setText(new Html(message));
        }else{
            message = "<p>Password reset failed for user:<br>" + email + "<br>No such user exists.</p>";
            dialog.setText(new Html(message));
        }
        dialog.setConfirmText("OK");
        dialog.open();
    }

}
