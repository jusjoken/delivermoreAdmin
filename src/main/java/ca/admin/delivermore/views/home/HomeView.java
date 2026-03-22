package ca.admin.delivermore.views.home;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import ca.admin.delivermore.views.MainLayout;

@PageTitle("Home")
@Route(value = "home", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@AnonymousAllowed
public class HomeView extends HorizontalLayout {

    public HomeView(@Autowired Environment env) {
        String version = env.getProperty("DM_APPLICATION_RELEASE_VERSION");
        String header = "Welcome to DeliverMore Admin application (v" + version + ")";

        Text welcomeMessage = new Text(header);

        setMargin(true);
        //setVerticalComponentAlignment(Alignment.END, welcomeMessage);

        add(welcomeMessage);
    }

}
