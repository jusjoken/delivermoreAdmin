package ca.admin.delivermore.components.custom;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.menubar.MenuBar;

import ca.admin.delivermore.collector.data.service.TeamsRepository;
import ca.admin.delivermore.collector.data.tookan.Team;
import ca.admin.delivermore.data.service.Registry;

public class LocationChoice extends CustomField<String> {

    private MenuBar menuBar = new MenuBar();
    private MenuItem item = menuBar.addItem("Location");

    private Boolean showAllOption = Boolean.FALSE;
    private static String lastLocatioinUsed = "LAST_LOCATION_USED";
    private String selectedLocation;
    private Long selectedLocationId = 0L;
    private Team allLocationOption = new Team(0L,null,0L,"All Locations",null,null,null);
    private Team noLocationsFound = new Team(1L,null,0L,"None found",null,null,null);

    private TeamsRepository teamsRepository;

    private List<LocationChoiceChangedListener> locationChoiceChangedListeners = new ArrayList<>();
    private List<Team> locations = new ArrayList<>();

    private Logger log = LoggerFactory.getLogger(LocationChoice.class);

    public LocationChoice(Boolean showAllOption) {
        this.showAllOption = showAllOption;
        this.teamsRepository = Registry.getBean(TeamsRepository.class);
        locations = teamsRepository.findByActiveTrueOrderByTeamNameAsc();
        SubMenu subMenu = item.getSubMenu();

        ComponentEventListener<ClickEvent<MenuItem>> listener = e -> {
            //selectedLocation = e.getSource().getText();
            //selectedLocationId = Long.valueOf(e.getSource().getId().get());
            setSelectedLocationId(Long.valueOf(e.getSource().getId().get()));
            /*
            for (MenuItem item: subMenu.getItems()) {
                if(!item.getText().equals(selectedLocation)){
                    item.setChecked(false);
                }
            }

             */
            notifyLocationChanged();
            log.info("Listener: clicked: " + e.getSource().getText() + " id:" + e.getSource().getId().get() + " selectedId:" + selectedLocationId);
        };

        if(this.showAllOption) {
            MenuItem allLocations = subMenu.addItem(allLocationOption.getTeamName(), listener);
            allLocations.setCheckable(true);
            allLocations.setId(allLocationOption.getTeamId().toString());
            allLocations.setChecked(true);
            subMenu.addComponent(new Hr());
        }

        log.info("LocationChoice: locations size:" + locations.size() + " locations:" + locations);
        for (Team location: locations) {
            MenuItem locationItem = subMenu.addItem(location.getTeamName(), listener);
            locationItem.setCheckable(true);
            locationItem.setId(location.getTeamId().toString());
            log.info("LocationChoice: adding: " + location.getTeamName());
        }
        setSelectedByTeam(allLocationOption);
    }

    @Override
    protected String generateModelValue() {
        return selectedLocation;
    }

    @Override
    protected void setPresentationValue(String s) {
        selectedLocation = s;
    }

    public Boolean getShowAllOption() {
        return showAllOption;
    }

    public void setShowAllOption(Boolean showAllOption) {
        this.showAllOption = showAllOption;
    }

    public String getSelectedLocation() {
        return selectedLocation;
    }

    public void setSelectedLocation(String selectedLocation) {
        clearSelected();
        //find id by name then set it as selected
        Team selTeam = teamsRepository.findByTeamName(selectedLocation);
        setSelectedByTeam(selTeam);
    }

    private void setSelectedByTeam(Team team){
        if(team==null){
            if(showAllOption){
                setSelectedByTeam(allLocationOption);
            }else{
                if(locations.size()>0){
                    setSelectedByTeam(locations.get(0));
                }else{
                    setSelectedByTeam(noLocationsFound);
                }
            }
        }else{
            this.selectedLocationId = team.getTeamId();
            this.selectedLocation = team.getTeamName();
            item.setText(team.getTeamName());
        }
    }

    public Long getSelectedLocationId() {
        return selectedLocationId;
    }

    public void setSelectedLocationId(Long selectedLocationId) {
        clearSelected();
        //find id by id then set it as selected
        Team selTeam = teamsRepository.findByTeamId(selectedLocationId);
        setSelectedByTeam(selTeam);
    }

    public MenuBar getMenuBar() {
        return menuBar;
    }

    public void addListener(LocationChoiceChangedListener listener){
        locationChoiceChangedListeners.add(listener);
    }

    private void clearSelected(){
        SubMenu subMenu = item.getSubMenu();
        for (MenuItem item: subMenu.getItems()) {
            item.setChecked(false);
        }
    }

    public Boolean isAllLocationsSelected(){
        if(selectedLocationId.equals(allLocationOption.getTeamId())){
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private void notifyLocationChanged(){
        for (LocationChoiceChangedListener listener: locationChoiceChangedListeners) {
            listener.locationChanged();
        }
    }

    public String getLastLocatioinUsed() {
        return lastLocatioinUsed;
    }

    public Team getNoLocationsFound() {
        return noLocationsFound;
    }

    public String getLocationNameById(Long teamId){
        if(teamId==null) return noLocationsFound.getTeamName();
        Team team = teamsRepository.findByTeamId(teamId);
        if(team==null) return noLocationsFound.getTeamName();
        return team.getTeamName();
    }

    public Team getLocationTeamById(Long teamId){
        if(teamId==null) return noLocationsFound;
        Team team = teamsRepository.findByTeamId(teamId);
        if(team==null) return noLocationsFound;
        return team;
    }

    public List<Team> getLocations() {
        return locations;
    }

    public Boolean isTeamNotFound(Team team){
        if(team==null){
            return Boolean.TRUE;
        }else{
            if(team.equals(noLocationsFound)) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
