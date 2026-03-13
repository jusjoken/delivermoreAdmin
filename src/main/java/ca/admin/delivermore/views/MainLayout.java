package ca.admin.delivermore.views;

import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.components.appnav.BrandExpression;
import ca.admin.delivermore.data.service.Registry;
import ca.admin.delivermore.data.service.intuit.domain.OAuth2Configuration;
import ca.admin.delivermore.security.AuthenticatedUser;
import ca.admin.delivermore.views.about.AboutView;
import ca.admin.delivermore.views.asset.TabletAssetHistoryView;
import ca.admin.delivermore.views.asset.TabletAssetsView;
import ca.admin.delivermore.views.drivers.*;
import ca.admin.delivermore.views.home.HomeView;
import ca.admin.delivermore.views.intuit.QBOConnectView;
import ca.admin.delivermore.views.login.PasswordReset;
import ca.admin.delivermore.views.report.PeriodSummaryView;
import ca.admin.delivermore.views.restaurants.RestInvoiceView;
import ca.admin.delivermore.views.restaurants.RestPayoutView;
import ca.admin.delivermore.views.restaurants.RestView;
import ca.admin.delivermore.views.tasks.TaskListView;
import ca.admin.delivermore.views.tasks.TasksByCustomerView;
import ca.admin.delivermore.views.tasks.TasksByDayAndWeekView;
import ca.admin.delivermore.views.tasks.TasksView;
import ca.admin.delivermore.views.utility.GiftCardView;
import ca.admin.delivermore.views.utility.TeamsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * The main view is a top-level placeholder for other views.
 */
public class MainLayout extends AppLayout {

    private Logger log = LoggerFactory.getLogger(MainLayout.class);

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    private H1 viewTitle;

    private AuthenticatedUser authenticatedUser;
    private AccessAnnotationChecker accessChecker;
    private OAuth2Configuration oAuth2Configuration;

    @Autowired
    Environment env;

    public MainLayout(AuthenticatedUser authenticatedUser, AccessAnnotationChecker accessChecker, Environment env) {
        this.authenticatedUser = authenticatedUser;
        this.accessChecker = accessChecker;
        this.oAuth2Configuration = Registry.getBean(OAuth2Configuration.class);
        if(env.containsProperty("security.enabled")){
            this.securityEnabled = Boolean.parseBoolean(env.getProperty("security.enabled"));
        }else{
            this.securityEnabled = true;
        }

        //TODO: load data from Tookan and then save to the TaskDetailRepository
        //create a method that provides a TaskEntity for each Task

        setPrimarySection(Section.DRAWER);
        addToNavbar(true, createHeaderContent());
        addToDrawer(createDrawerContent());
    }

    private Component createHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addClassNames("view-toggle");
        toggle.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle = new H1();
        viewTitle.addClassNames("view-title");

        Header header = new Header(toggle, viewTitle);
        header.addClassNames("view-header");
        return header;
    }

    private Component createDrawerContent() {
        String text = "DeliverMore";
        com.vaadin.flow.component.html.Section section = new com.vaadin.flow.component.html.Section(new BrandExpression(text), createFooter(),createNavigation());
        section.addClassNames("drawer-section");
        return section;
    }
    
    private SideNav createNavigation() {
        // SideNav is not yet an official component.
        // For documentation, visit https://github.com/vaadin/vcf-nav#readme
        SideNav nav = new SideNav();
        nav.addClassNames("app-nav");

        if (checkAccess(HomeView.class)) {
            nav.addItem(new SideNavItem("Home", HomeView.class, VaadinIcon.HOME.create()));
            //nav.addItem(new SideNavItem("Home", HomeView.class, "la la-home"));

        }
        if (checkAccess(AboutView.class)) {
            nav.addItem(new SideNavItem("About", AboutView.class, VaadinIcon.QUESTION_CIRCLE.create()));
            //nav.addItem(new SideNavItem("About", AboutView.class, "la la-question-circle"));

        }
        if (checkAccess(MyPayView.class)) {
            nav.addItem(new SideNavItem("My Pay", MyPayView.class, VaadinIcon.DOLLAR.create()));
            //nav.addItem(new SideNavItem("My Pay", MyPayView.class, "la la-dollar-sign"));

        }

        if (checkAccess(DriverRedeemGiftCardView.class)) {
            nav.addItem(new SideNavItem("Redeem Gift Card", DriverRedeemGiftCardView.class, VaadinIcon.CREDIT_CARD.create()));
            //nav.addItem(new SideNavItem("Redeem Gift Card", DriverRedeemGiftCardView.class, "la la-credit-card"));

        }

        if (checkAccess(ScheduleView.class)) {
            nav.addItem(new SideNavItem("Schedule", ScheduleView.class, VaadinIcon.CALENDAR.create()));
            //nav.addItem(new SideNavItem("Schedule", ScheduleView.class, "la la-calendar"));

        }

        //only add the menu folder if the user has access to at least one of the sub views
        if (checkAccess(DriversView.class)
                || checkAccess(RestView.class)
                || checkAccess(TeamsView.class)
                || checkAccess(TaskListView.class)
                || checkAccess(QBOConnectView.class)
                || checkAccess(DriverAdjustmentTemplateView.class)
                || checkAccess(GiftCardView.class)
                || checkAccess(TabletAssetsView.class)
                || checkAccess(TabletAssetHistoryView.class)
                || checkAccess(TasksView.class)) {
            SideNavItem utilities = new SideNavItem("Utilities");
            utilities.setPrefixComponent(VaadinIcon.FOLDER_OPEN.create());
            nav.addItem(utilities);
            if (checkAccess(DriversView.class)) {
                SideNavItem driversMenu = new SideNavItem("Drivers", DriversView.class, VaadinIcon.CAR.create());
                //SideNavItem driversMenu = new SideNavItem("Drivers", DriversView.class, "la la-car-side");
                utilities.addItem(driversMenu);
            }
            if (checkAccess(DriverAdjustmentTemplateView.class)) {
                SideNavItem driverAdjustMenu = new SideNavItem("Driver Adj Templates", DriverAdjustmentTemplateView.class, VaadinIcon.CAR.create());
                //SideNavItem driverAdjustMenu = new SideNavItem("Driver Adj Templates", DriverAdjustmentTemplateView.class, "la la-car-side");
                utilities.addItem(driverAdjustMenu);
            }
            if (checkAccess(TeamsView.class)) {
                SideNavItem teamsMenu = new SideNavItem("Locations", TeamsView.class, VaadinIcon.MAP_MARKER.create());
                //SideNavItem teamsMenu = new SideNavItem("Locations", TeamsView.class, "la la-map-marked-alt");
                utilities.addItem(teamsMenu);
            }
            if (checkAccess(RestView.class)) {
                SideNavItem restMenu = new SideNavItem("Restaurants", RestView.class, VaadinIcon.SHOP.create());
                //SideNavItem restMenu = new SideNavItem("Restaurants", RestView.class, "la la-store-alt");
                utilities.addItem(restMenu);
            }
            if (checkAccess(TaskListView.class)) {
                SideNavItem taskListMenu = new SideNavItem("Task List", TaskListView.class, VaadinIcon.LIST.create());
                //SideNavItem taskListMenu = new SideNavItem("Task List", TaskListView.class, "la la-stack-overflow");
                utilities.addItem(taskListMenu);
            }
            if (checkAccess(GiftCardView.class)) {
                SideNavItem giftCardListMenu = new SideNavItem("Gift Card List", GiftCardView.class, VaadinIcon.CREDIT_CARD.create());
                //SideNavItem giftCardListMenu = new SideNavItem("Gift Card List", GiftCardView.class, "la la-credit-card");
                utilities.addItem(giftCardListMenu);
            }
            if (checkAccess(TabletAssetsView.class)) {
                SideNavItem assetListMenu = new SideNavItem("Asset Manager", TabletAssetsView.class, VaadinIcon.TABLET.create());
                utilities.addItem(assetListMenu);
            }
            if (checkAccess(TabletAssetHistoryView.class)) {
                SideNavItem assetListMenu = new SideNavItem("Asset History Browser", TabletAssetHistoryView.class, VaadinIcon.TABLET.create());
                utilities.addItem(assetListMenu);
            }
            if(oAuth2Configuration.isConfigured()){
                if (checkAccess(QBOConnectView.class)) {
                    SideNavItem qboConnectMenu = new SideNavItem("QBO connect", QBOConnectView.class, VaadinIcon.CONNECT_O.create());
                    //SideNavItem qboConnectMenu = new SideNavItem("QBO connect", QBOConnectView.class, "la la-network-wired");
                    utilities.addItem(qboConnectMenu);
                }
            }
            if (checkAccess(TasksView.class)) {
                SideNavItem tasksMenu = new SideNavItem("Tasks(under dev)", TasksView.class, VaadinIcon.LIST.create());
                //SideNavItem tasksMenu = new SideNavItem("Tasks(under dev)", TasksView.class, "la la-stack-overflow");
                utilities.addItem(tasksMenu);
            }
        }

        //only add the menu folder if the user has access to at least one of the sub views
        if (checkAccess(TasksByCustomerView.class)
                || checkAccess(TasksByDayAndWeekView.class)
                || checkAccess(PeriodSummaryView.class)
                || checkAccess(DriverReportView.class)) {
            SideNavItem reports = new SideNavItem("Reports");
            reports.setPrefixComponent(VaadinIcon.FOLDER_OPEN.create());
            nav.addItem(reports);
            if (checkAccess(TasksByCustomerView.class)) {
                SideNavItem customerTasksMenu = new SideNavItem("Tasks by Customer", TasksByCustomerView.class, VaadinIcon.USER.create());
                //SideNavItem customerTasksMenu = new SideNavItem("Tasks by Customer", TasksByCustomerView.class, "la la-user");
                reports.addItem(customerTasksMenu);
            }
            if (checkAccess(TasksByDayAndWeekView.class)) {
                SideNavItem tasksByDayAndWeekMenu = new SideNavItem("Tasks Report", TasksByDayAndWeekView.class, VaadinIcon.CALENDAR.create());
                reports.addItem(tasksByDayAndWeekMenu);
            }
            if (checkAccess(PeriodSummaryView.class)) {
                SideNavItem periodSummaryMenu = new SideNavItem("Period Summary", PeriodSummaryView.class, VaadinIcon.CALENDAR_BRIEFCASE.create());
                //SideNavItem periodSummaryMenu = new SideNavItem("Period Summary", PeriodSummaryView.class, "la la-calendar");
                reports.addItem(periodSummaryMenu);
            }
            if (checkAccess(DriverReportView.class)) {
                SideNavItem periodSummaryMenu = new SideNavItem("Driver Report", DriverReportView.class, VaadinIcon.CAR.create());
                //SideNavItem periodSummaryMenu = new SideNavItem("Driver Report", DriverReportView.class, "la la-car-side");
                reports.addItem(periodSummaryMenu);
            }
        }

        if (checkAccess(DriverPayoutView.class)) {
            nav.addItem(new SideNavItem("Driver Payouts", DriverPayoutView.class, VaadinIcon.DOLLAR.create()));
            //nav.addItem(new SideNavItem("Driver Payouts", DriverPayoutView.class, "la la-portrait"));

        }
        if (checkAccess(RestPayoutView.class)) {
            nav.addItem(new SideNavItem("Restaurant Payouts", RestPayoutView.class, VaadinIcon.INVOICE.create()));
            //nav.addItem(new SideNavItem("Restaurant Payouts", RestPayoutView.class, "la la-file-invoice-dollar"));

        }
        if (checkAccess(RestInvoiceView.class)) {
            nav.addItem(new SideNavItem("Invoiced Vendors", RestInvoiceView.class, VaadinIcon.INVOICE.create()));
            //nav.addItem(new SideNavItem("Invoiced Vendors", RestInvoiceView.class, "la la-file-invoice-dollar"));

        }
        if (checkAccess(PasswordReset.class)) {
            nav.addItem(new SideNavItem("Reset password", PasswordReset.class, VaadinIcon.PASSWORD.create()));
            //nav.addItem(new SideNavItem("Reset password", PasswordReset.class, "la la-key"));

        }

        return nav;
    }

    private Boolean checkAccess(Class<?> cls){
        if(securityEnabled){
            return accessChecker.hasAccess(cls);
        }else{
            return true;
        }
    }

    //NOTE: foot moved to the top of the side menu at request of owner
    private Footer createFooter() {
        Footer layout = new Footer();
        layout.addClassNames("app-nav-footer");
        String securityDisabledString = "";
        if(!securityEnabled) securityDisabledString = " (Security Disabled)";

        Optional<Driver> maybeUser = authenticatedUser.get();
        if (maybeUser.isPresent()) {
            Driver user = maybeUser.get();

            Avatar avatar = new Avatar(user.getName(), user.getFleetThumbImage());
            avatar.addClassNames("me-xs");

            ContextMenu userMenu = new ContextMenu(avatar);
            userMenu.setOpenOnClick(true);
            userMenu.addItem("Logout", e -> {
                authenticatedUser.logout();
            });

            Span name = new Span(user.getName());
            name.addClassNames("font-medium", "text-s", "text-secondary");

            layout.add(avatar, name);
        } else {
            Anchor loginLink = new Anchor("login", "Sign in" + securityDisabledString);
            layout.add(loginLink);
        }

        return layout;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }
}
