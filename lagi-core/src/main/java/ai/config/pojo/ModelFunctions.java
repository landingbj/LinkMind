package ai.config.pojo;

import ai.common.pojo.Backend;
import ai.common.pojo.EmbeddingConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@ToString
@Data
public class ModelFunctions {
    private List<EmbeddingConfig> embedding;
    @JsonProperty("chat")
    private ModelFunction chat;
    @JsonProperty("chatVL")
    private List<Backend> chatVL;
    @JsonProperty("speech2text")
    private List<Backend> speech2text;
    @JsonProperty("text2speech")
    private List<Backend> text2speech;
    @JsonProperty("text2image")
    private List<Backend> text2image;
    @JsonProperty("image2text")
    private List<Backend> image2text;
    @JsonProperty("image2enhance")
    private List<Backend> image2Enhance;
    @JsonProperty("text2video")
    private List<Backend> text2video;
    @JsonProperty("image2video")
    private List<Backend> image2video;
    @JsonProperty("video2track")
    private List<Backend> video2Track;
    @JsonProperty("video2enhance")
    private List<Backend> video2Enhance;
    @JsonProperty("translate")
    private List<Backend> translate;
    @JsonProperty("speech2clone")
    private List<Backend> speech2clone;
    @JsonProperty("image2ocr")
    private List<Backend> image2ocr;
    @JsonProperty("doc2ocr")
    private List<Backend> doc2orc;
    @JsonProperty("doc2instruct")
    private List<Backend> doc2instruct;
    @JsonProperty("text2sql")
    private List<Backend> text2sql;
    @JsonProperty("doc2ext")
    private List<Backend> doc2ext;
    @JsonProperty("doc2struct")
    private List<Backend> doc2struct;
    @JsonProperty("filter")
    private String filter;
    @JsonProperty("text2qa")
    private List<Backend> text2qa;
    @JsonProperty("image2detect")
    private List<Backend> image2detect;

    @JsonCreator
    public ModelFunctions(
            @JsonProperty("embedding") List<EmbeddingConfig> embedding,
            @JsonProperty("chat") ModelFunction chat,
            @JsonProperty("speech2text") List<Backend> speech2text,
            @JsonProperty("text2speech") List<Backend> text2speech,
            @JsonProperty("text2image") List<Backend> text2image,
            @JsonProperty("image2text") List<Backend> image2text,
            @JsonProperty("image2enhance") List<Backend> image2Enhance,
            @JsonProperty("text2video") List<Backend> text2video,
            @JsonProperty("image2video") List<Backend> image2video,
            @JsonProperty("video2track") List<Backend> video2Track,
            @JsonProperty("video2enhance") List<Backend> video2Enhance,
            @JsonProperty("translate") List<Backend> translate,
            @JsonProperty("speech2clone") List<Backend> speech2clone,
            @JsonProperty("image2ocr") List<Backend> image2ocr,
            @JsonProperty("text2sql") List<Backend> text2sql,
            @JsonProperty("doc2ocr") List<Backend> doc2orc,
            @JsonProperty("doc2instruct") List<Backend> doc2instruct,
            @JsonProperty("doc2ext") List<Backend> doc2ext,
            @JsonProperty("doc2struct") List<Backend> doc2struct,
            @JsonProperty("filter") String filter,
            @JsonProperty("text2qa") List<Backend> text2qa,
            @JsonProperty("image2detect") List<Backend> image2detect
    ) {
        this.embedding = embedding;
        this.chat = chat;
        this.speech2text = speech2text;
        this.text2speech = text2speech;
        this.text2image = text2image;
        this.image2text = image2text;
        this.image2Enhance = image2Enhance;
        this.text2video = text2video;
        this.image2video = image2video;
        this.video2Track = video2Track;
        this.video2Enhance = video2Enhance;
        this.translate = translate;
        this.speech2clone = speech2clone;
        this.image2ocr = image2ocr;
        this.text2sql = text2sql;
        this.doc2orc = doc2orc;
        this.doc2instruct = doc2instruct;
        this.doc2ext = doc2ext;
        this.doc2struct = doc2struct;
        this.filter = filter;
        this.text2qa = text2qa;
        this.image2detect = image2detect;
    }
}
