package net.minecraft.server;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class BanEntry<T> extends StoredUserEntry<T> {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    protected final Date startDate;
    protected final String source;
    protected final Date expirationDate;
    protected final String reason;

    public BanEntry(T user, Date startDate, String source, Date expirationDate, String reason) {
        super(user);
        this.startDate = startDate == null ? new Date() : startDate;
        this.source = source == null ? "(Unknown)" : source;
        this.expirationDate = expirationDate;
        this.reason = reason == null ? "Banned by an operator." : reason;
    }

    protected BanEntry(T object, JsonObject jsonObject) {
        super(object, jsonObject);

        Date date;
        try {
            date = jsonObject.has("created") ? DATE_FORMAT.parse(jsonObject.get("created").getAsString()) : new Date();
        } catch (ParseException parseexception1) {
            date = new Date();
        }

        this.startDate = date;
        this.source = jsonObject.has("source") ? jsonObject.get("source").getAsString() : "(Unknown)";

        Date date1;
        try {
            date1 = jsonObject.has("expires") ? DATE_FORMAT.parse(jsonObject.get("expires").getAsString()) : null;
        } catch (ParseException parseexception) {
            date1 = null;
        }

        this.expirationDate = date1;
        this.reason = jsonObject.has("reason") ? jsonObject.get("reason").getAsString() : "Banned by an operator.";
    }

    public Date getExpirationDate() {
        return this.expirationDate;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    boolean hasExpired() {
        return this.expirationDate != null && this.expirationDate.before(new Date());
    }

    @Override
    protected void serialize(JsonObject json) {
        json.addProperty("created", DATE_FORMAT.format(this.startDate));
        json.addProperty("source", this.source);
        json.addProperty("expires", this.expirationDate == null ? "forever" : DATE_FORMAT.format(this.expirationDate));
        json.addProperty("reason", this.reason);
    }
}
