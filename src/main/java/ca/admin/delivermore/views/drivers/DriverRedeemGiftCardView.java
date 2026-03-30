package ca.admin.delivermore.views.drivers;

import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.components.custom.ButtonNumberField;
import ca.admin.delivermore.components.custom.Divider;
import ca.admin.delivermore.data.entity.GiftCardEntity;
import ca.admin.delivermore.data.entity.GiftCardTranactionEntity;
import ca.admin.delivermore.data.service.GiftCardRepository;
import ca.admin.delivermore.data.service.GiftCardTranactionRepository;
import ca.admin.delivermore.security.AuthenticatedUser;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import com.vaadin.flow.theme.lumo.LumoIcon;

@PageTitle("Redeem Gift Card")
@Route(value = "redeemgiftcard", layout = MainLayout.class)
@RolesAllowed("USER")
public class DriverRedeemGiftCardView extends VerticalLayout {

    private Logger log = LoggerFactory.getLogger(DriverRedeemGiftCardView.class);
    private AuthenticatedUser authenticatedUser;
    Optional<Driver> signedInDriver;

    private VerticalLayout mainLayout = UIUtilities.getVerticalLayout();
    private VerticalLayout giftCardSearchResultLayout = UIUtilities.getVerticalLayout(false,true,false);
    GiftCardEntity giftCard;

    GiftCardRepository giftCardRepository;
    GiftCardTranactionRepository giftCardTranactionRepository;
    private EmailService emailService;

    public DriverRedeemGiftCardView(GiftCardRepository giftCardRepository, GiftCardTranactionRepository giftCardTranactionRepository, AuthenticatedUser authenticatedUser, EmailService emailService) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardTranactionRepository = giftCardTranactionRepository;
        this.authenticatedUser = authenticatedUser;
        this.emailService = emailService;

        signedInDriver = getSignedInDriver();
        log.info("signedInDriver:" + signedInDriver);

        mainLayout.add(getGiftCardLookup());
        setSizeFull();
        mainLayout.setSizeFull();
        add(mainLayout);

    }

    private VerticalLayout getGiftCardLookup() {
        VerticalLayout giftCardLayout = UIUtilities.getVerticalLayout();
        HorizontalLayout giftCardSearchLayout = UIUtilities.getHorizontalLayout();
        giftCardSearchLayout.setVerticalComponentAlignment(Alignment.BASELINE);
        giftCardSearchLayout.setAlignItems(Alignment.BASELINE);
        TextField searchBox = UIUtilities.getTextField("Gift Card Code");
        searchBox.setAutofocus(true);
        searchBox.setClearButtonVisible(true);
        Button searchButton = UIUtilities.createSmallButton("Find");
        searchButton.addClickListener(e -> {
            String searchString = searchBox.getValue();
            if(!searchString.startsWith("DM")){
                searchString = "DM" + searchString;
            }
            giftCard = giftCardRepository.findByCodeIgnoreCase(searchString);
            buildGiftCardSearchResults();
        });
        giftCardSearchLayout.add(searchBox,searchButton);
        giftCardLayout.add(giftCardSearchLayout,new Divider(),giftCardSearchResultLayout);
        return giftCardLayout;
    }

    private void buildGiftCardSearchResults() {
        giftCardSearchResultLayout.removeAll();
        if(giftCard==null){
            NativeLabel noResultsLabel = new NativeLabel("No Gift Card Found");
            giftCardSearchResultLayout.add(noResultsLabel);
        }else{
            log.info("buildGiftCardSearchResults: Gift Card:" + giftCard);
            HorizontalLayout resultRow1 = UIUtilities.getHorizontalLayout();
            HorizontalLayout resultRow2 = UIUtilities.getHorizontalLayout();
            TextField resultCode = UIUtilities.getTextFieldRO("Code", giftCard.getCode());
            TextField resultDateIssued = UIUtilities.getTextFieldRO("Issued", giftCard.getIssued().toString());
            TextField resultCustomerName = UIUtilities.getTextFieldRO("Customer", giftCard.getCustomerName(),"300px");
            resultRow1.add(resultCode,resultDateIssued);
            TextField resultBalance = UIUtilities.getTextFieldRO("Balance", giftCard.getBalance().toString());
            resultRow2.add(resultBalance,resultCustomerName);
            giftCardSearchResultLayout.add(resultRow1,resultRow2);
            if(giftCard.getNotes()!=null && !giftCard.getNotes().isEmpty()){
                TextField resultNotes = UIUtilities.getTextFieldRO("Notes", giftCard.getNotes());
                giftCardSearchResultLayout.add(resultNotes);
            }
            giftCardSearchResultLayout.add(new Divider());

            if(giftCard.getBalance()>0.0){
                //add redemption options
                Button redeemFull = UIUtilities.createSmallButton("Redeem Full (" + giftCard.getBalance().toString() + ")");
                redeemFull.addClickListener(e -> {
                    //call function to confirm and then adjust gift card balance
                    redeemAmount(giftCard.getBalance());
                });
                ButtonNumberField redeemPart = UIUtilities.getButtonNumberField("Redeem portion", false, "$");
                redeemPart.setButtonIcon(LumoIcon.CHECKMARK.create());
                redeemPart.focus();
                redeemPart.addClickListener(e -> {
                    //call function to confirm and then adjust gift card balance
                    if(redeemPart.getValue()!=null && redeemPart.getValue()>0.0){
                        if(redeemPart.getValue()>giftCard.getBalance()){
                            redeemAmount(giftCard.getBalance());
                        }else{
                            redeemAmount(redeemPart.getValue());
                        }
                    }
                });
                giftCardSearchResultLayout.add(redeemFull,redeemPart);
            }else{
                NativeLabel noBalanceLabel = new NativeLabel("Gift Card has no balance remaining");
                giftCardSearchResultLayout.add(noBalanceLabel);
            }

        }
    }

    private void redeemAmount(Double redeemAmount) {
        //confirm redemption first
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Redeem Gift Card amount?");
        dialog.setText(
                "Redeem $" + redeemAmount + " from gift card " + giftCard.getCode() + "?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Redeem");
        //dialog.setConfirmButtonTheme("success primary");
        dialog.addConfirmListener(event -> {
            GiftCardTranactionEntity giftCardTranaction = new GiftCardTranactionEntity();
            giftCardTranaction.setAmount(redeemAmount);
            giftCardTranaction.setCode(giftCard.getCode());
            giftCardTranaction.setUserName(signedInDriver.get().getName());
            giftCardTranactionRepository.save(giftCardTranaction);

            //send email to customer and support with amount and balance
            String email = "support@delivermore.ca";
            if(!giftCard.getCustomerEmail().isEmpty()){
                email += "," + giftCard.getCustomerEmail();
            }
            String balanceInfo = "";
            if(giftCard.getBalance()>0){
                balanceInfo = "Your new balance is $" + giftCard.getBalance() + ".<br><br>";
            }else{
                balanceInfo = "Your card no longer has a balance.<br><br>";
            }
            String thanks = "Thanks for using DeliverMore!<br><br>Visit us at <a href=\"https://delivermore.ca\">delivermore.ca</a>";
            String body = "<p>Thanks for using your gift card.  An amount of $" + redeemAmount + " has been deducted from your balance.<br><br>" + balanceInfo + thanks + "</p>";
            String subject = "DeliverMore Gift Card " + giftCard.getCode() + " redeemed";
            emailService.sendMailWithHtmlBody(email,subject, body);
            UIUtilities.showNotification("Gift Card information sent");

            buildGiftCardSearchResults();
        });
        dialog.open();
    }

    private Optional<Driver> getSignedInDriver() {
        return authenticatedUser.get();
    }

}
