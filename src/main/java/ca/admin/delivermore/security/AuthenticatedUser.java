package ca.admin.delivermore.security;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;

import ca.admin.delivermore.collector.data.entity.PasswordResetToken;
import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.service.PasswordResetTokenRepository;
import ca.admin.delivermore.collector.data.tookan.Driver;

@Component
public class AuthenticatedUser {

    private final Logger log = LoggerFactory.getLogger(AuthenticatedUser.class);
    private final DriversRepository driversRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailService emailService;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    public AuthenticatedUser(DriversRepository driversRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.driversRepository = driversRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    private Optional<Authentication> getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        return Optional.ofNullable(context.getAuthentication())
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken));
    }

    public Optional<Driver> get() {
        return getAuthentication().flatMap(authentication -> {
            Driver byAllowedLogin = driversRepository.findDriverByEmailAndLoginAllowed(authentication.getName(), Boolean.TRUE);
            if (byAllowedLogin != null) {
                return Optional.of(byAllowedLogin);
            }

            // Keep the current session resolvable even if loginAllowed was toggled after login.
            Optional<Driver> fallback = driversRepository.findAll().stream()
                    .filter(driver -> Objects.equals(driver.getEmail(), authentication.getName()))
                    .findFirst();

            if (fallback.isPresent()) {
                log.debug("AuthenticatedUser.get: resolved user '{}' via fallback email lookup despite loginAllowed mismatch", authentication.getName());
            } else {
                log.warn("AuthenticatedUser.get: no driver found for authenticated principal '{}'", authentication.getName());
            }

            return fallback;
        });
    }

    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.logout(VaadinServletRequest.getCurrent().getHttpServletRequest(), null, authentication);
        UI.getCurrent().getPage().setLocation(SecurityConfiguration.LOGOUT_SUCCESS_URL);
    }

    public Boolean resetPassword(String email){
        Driver user;
        try {
            user = driversRepository.findDriverByEmailAndLoginAllowed(email,Boolean.TRUE);
        } catch (Exception e) {
            log.info("resetPassword: failed:" + e);
            return false;
        }
        if(user==null){
            log.info("resetPassword: user not valid");
            return false;
        }
        log.info("resetPassword: resetting password for user:" + user);
        String token = UUID.randomUUID().toString();
        createPasswordResetTokenForUser(user, token);
        //log.info("resetPassword: send email here to:" + user.getEmail() + " token:" + token);
        UI.getCurrent().getPage().fetchCurrentURL(currentUrl -> {
            // Note that this method runs asynchronously
            sendToken(currentUrl, token, email);
        });
        return true;
    }

    private void sendToken(URL url, String token, String email){
        //log.info("sendToken: url:" + url);
        String baseurl = getBaseURL(url);
        if(!baseurl.isEmpty()){
            String fullurl = baseurl + "/updatepassword?token=" + token;
            //log.info("sendToken: fullurl:" + fullurl);
            String body = "<p>To reset your password use the following link within 24 hours.<br><br>" + fullurl + "</p>";
            //log.info("sendToken: body:" + body);
            emailService.sendMailWithHtmlBody(email,"DeliverMore password reset requested", body);
        }
    }

    private String getBaseURL(URL url){
        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        try {
            URL urlOut = new URL(protocol, host, port, "");
            return urlOut.toString();
        } catch (MalformedURLException e) {
            return "";
        }
    }

    public void createPasswordResetTokenForUser(final Driver user, final String token) {
        final PasswordResetToken myToken = new PasswordResetToken(token, user);
        passwordResetTokenRepository.save(myToken);
    }

    public String validatePasswordResetToken(String token) {
        final PasswordResetToken passToken = passwordResetTokenRepository.findByToken(token);

        return !isTokenFound(passToken) ? "invalidToken"
                : isTokenExpired(passToken) ? "expired"
                : null;
    }

    private boolean isTokenFound(PasswordResetToken passToken) {
        //log.info("isTokenFound:" + passToken + " returning:" + (passToken != null));
        return passToken != null;
    }

    private boolean isTokenExpired(PasswordResetToken passToken) {
        final Calendar cal = Calendar.getInstance();
        //log.info("isTokenExpired:" + passToken + " returning:" + (passToken.getExpiryDate().before(cal.getTime())));
        return passToken.getExpiryDate().before(cal.getTime());
    }

    public Boolean savePassword(String newPassword, String token){
        String checkToken = validatePasswordResetToken(token);
        if(checkToken!=null){
            //log.info("savePassword: token check failed:" + token);
            return false;
        }
        Driver user = getUserByPasswordResetToken(token);
        if(user==null){
            //log.info("savePassword: user not found by token:" + token);
            return false;
        }
        //save the password
        user.setHashedPassword(passwordEncoder.encode(newPassword));
        driversRepository.save(user);
        log.info("savePassword: password saved to user:" + user.getName());
        return true;
    }

    public Driver getUserByPasswordResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token) .getUser();
    }

}
