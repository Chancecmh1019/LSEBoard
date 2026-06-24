package com.lseboard.app.Model;

/**
 * LINE Emoji 模型
 * Emoji 與 Sticker 類似，但有不同的下載來源和類型
 */
public class Emoji {
    public enum Type {
        STATIC,
        ANIMATED
    }

    private Type type;
    private int id;
    private String productId; // Emoji 產品 ID

    public Emoji(Type type, int id, String productId) {
        this.type = type;
        this.id = id;
        this.productId = productId;
    }

    public Emoji(Type type, int id) {
        this.type = type;
        this.id = id;
        this.productId = "";
    }

    boolean equal(Emoji emoji) {
        return this.id == emoji.getId();
    }

    public Type getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}

