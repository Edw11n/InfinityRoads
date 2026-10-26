package com.infinityroads.geometry;

/**
 * Representa las cuatro direcciones cardinales de las carreteras procedurales
 * de InfinityRoads y sus materiales de concreto correspondientes.
 */
public enum RoadDirection {
    NORTH("YELLOW_CONCRETE", "Amarillo", 0, -1),
    SOUTH("BLUE_CONCRETE", "Azul", 0, 1),
    EAST("RED_CONCRETE", "Rojo", 1, 0),
    WEST("GREEN_CONCRETE", "Verde", -1, 0);

    private final String concreteMaterial;
    private final String colorName;
    private final int stepX;
    private final int stepZ;

    RoadDirection(String concreteMaterial, String colorName, int stepX, int stepZ) {
        this.concreteMaterial = concreteMaterial;
        this.colorName = colorName;
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    public String getConcreteMaterial() {
        return concreteMaterial;
    }

    public String getColorName() {
        return colorName;
    }

    public int getStepX() {
        return stepX;
    }

    public int getStepZ() {
        return stepZ;
    }
}
