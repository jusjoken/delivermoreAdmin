package ca.admin.delivermore.data.service;

public enum MenuImageSlot {
    RESTAURANT_LOGO("SQUARE", 1, 1, 512, 512),
    MENU_HEADER("RECTANGLE", 16, 9, 1280, 720),
    MENU_GROUP("RECTANGLE", 16, 9, 1280, 720),
    MENU_ITEM("RECTANGLE", 16, 9, 1280, 720);

    private final String shapeType;
    private final int aspectWidth;
    private final int aspectHeight;
    private final int maxWidth;
    private final int maxHeight;

    MenuImageSlot(String shapeType, int aspectWidth, int aspectHeight, int maxWidth, int maxHeight) {
        this.shapeType = shapeType;
        this.aspectWidth = aspectWidth;
        this.aspectHeight = aspectHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public String getShapeType() {
        return shapeType;
    }

    public int getAspectWidth() {
        return aspectWidth;
    }

    public int getAspectHeight() {
        return aspectHeight;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}