package au.org.ala.spatial.scatterplot

/**
 * A dto that represents a legend item
 *
 * @author Natasha Quimby (natasha.quimby@csiro.au)
 */
class LegendItemDTO {
    String name = "Unknown"//a null name defaults to "Unknown"
    Integer count
    Integer red
    Integer green
    Integer blue

    LegendItemDTO() {

    }

    /**
     * @return the name
     */
    String getName() {
        return name
    }

    /**
     * @param name the name to set
     */
    void setName(String name) {
        this.name = name
    }

    /**
     * @return the count
     */
    Integer getCount() {
        return count
    }

    /**
     * @param count the count to set
     */
    void setCount(Integer count) {
        this.count = count
    }

    /**
     * @return the red
     */
    Integer getRed() {
        return red
    }

    /**
     * @param red the red to set
     */
    void setRed(Integer red) {
        this.red = red
    }

    /**
     * @return the green
     */
    Integer getGreen() {
        return green
    }

    /**
     * @param green the green to set
     */
    void setGreen(Integer green) {
        this.green = green
    }

    /**
     * @return the blue
     */
    Integer getBlue() {
        return blue
    }

    /**
     * @param blue the blue to set
     */
    void setBlue(Integer blue) {
        this.blue = blue
    }


}
