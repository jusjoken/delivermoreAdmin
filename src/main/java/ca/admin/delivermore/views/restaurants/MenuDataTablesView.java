package ca.admin.delivermore.views.restaurants;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.data.service.RestaurantMenuEditorService;
import ca.admin.delivermore.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Menu Data Tables")
@Route(value = "utilities/menu-data-tables", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class MenuDataTablesView extends VerticalLayout {

    public MenuDataTablesView(RestaurantMenuEditorService restaurantMenuEditorService) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        Button openEditor = new Button("Open Menu Editor", event -> getUI().ifPresent(ui -> ui.navigate("restaurants/menu-editor")));
        openEditor.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        H3 title = new H3("Menu Data Tables");
        Span helper = new Span("Use Data Tables to manage shared checkout and menu configuration lists.");
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout actions = new HorizontalLayout(new MenuDataTablesMenuBar(restaurantMenuEditorService, () -> {
        }));
        actions.setPadding(false);
        actions.setSpacing(true);

        add(openEditor, title, helper, actions);
    }
}
