package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * downup事件中的单个事件实体类
 */
@Data
public class DownUpEvent {

    /** 方向（up=上车，down=下车） */
    @JsonProperty("direction")
    private String direction;

    /** 特征数据 */
    @JsonProperty("feature")
    private String feature;

    /** 图片数据 */
    @JsonProperty("image")
    private String image;

    /** 边界框X坐标 */
    @JsonProperty("box_x")
    private Integer boxX;

    /** 边界框Y坐标 */
    @JsonProperty("box_y")
    private Integer boxY;

    /** 边界框宽度 */
    @JsonProperty("box_w")
    private Integer boxW;

    /** 边界框高度 */
    @JsonProperty("box_h")
    private Integer boxH;

    // 手动添加getter方法以确保兼容性
    public String getDirection() {
        return direction;
    }

    public String getFeature() {
        return feature;
    }

    public String getImage() {
        return image;
    }

    public Integer getBoxX() {
        return boxX;
    }

    public Integer getBoxY() {
        return boxY;
    }

    public Integer getBoxW() {
        return boxW;
    }

    public Integer getBoxH() {
        return boxH;
    }

    // 手动添加setter方法以确保兼容性
    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setBoxX(Integer boxX) {
        this.boxX = boxX;
    }

    public void setBoxY(Integer boxY) {
        this.boxY = boxY;
    }

    public void setBoxW(Integer boxW) {
        this.boxW = boxW;
    }

    public void setBoxH(Integer boxH) {
        this.boxH = boxH;
    }
}
