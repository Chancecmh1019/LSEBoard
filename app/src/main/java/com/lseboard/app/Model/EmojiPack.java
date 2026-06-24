package com.lseboard.app.Model;

import java.util.ArrayList;

/**
 * LINE Emoji 包模型
 */
public class EmojiPack {
    private String title;
    private String productId; // Emoji 產品 ID (字串格式)
    private Emoji.Type type;
    private ArrayList<Integer> ids;
    private boolean visible;

    public EmojiPack(String title, String productId, Emoji.Type type, ArrayList<Integer> ids) {
        this.title = title;
        this.productId = productId;
        this.type = type;
        this.ids = ids;
        this.visible = true;
    }

    public int getId(int index) {
        return ids.get(index);
    }

    public ArrayList<Integer> getIds() {
        return ids;
    }

    public Emoji.Type getType() {
        return type;
    }

    public Emoji getEmoji(int index) {
        return new Emoji(type, ids.get(index), productId);
    }

    public int getCount() {
        return ids.size();
    }

    public String getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public boolean getVisible() {
        return visible;
    }

    public void setVisible(boolean b) {
        visible = b;
    }
}

