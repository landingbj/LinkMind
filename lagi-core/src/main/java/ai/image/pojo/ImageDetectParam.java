package ai.image.pojo;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class ImageDetectParam {
    private String model;
    private String imageUrl;
}
