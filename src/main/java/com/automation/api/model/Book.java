package com.automation.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Book {

    @JsonProperty("isbn")
    private String isbn;

    @JsonProperty("title")
    private String title;

    @JsonProperty("subTitle")
    private String subTitle;

    @JsonProperty("author")
    private String author;

    @JsonProperty("publish_date")
    private String publishDate;

    @JsonProperty("publisher")
    private String publisher;

    @JsonProperty("pages")
    private int pages;

    @JsonProperty("description")
    private String description;

    @JsonProperty("website")
    private String website;
}
