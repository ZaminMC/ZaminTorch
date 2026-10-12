package net.minecraft.stat;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.criterion.StatCriterion;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class Stat {
    public final String key;
    private final Text name;
    public boolean local;
    private final StatFormatter formatter;
    private final ScoreboardCriterion criterion;
    private Class<? extends StatProgress> progressType;
    private static NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    public static StatFormatter NUMBER_FORMATTER = new StatFormatter() {
        @Override
        public String format(int value) {
            return Stat.NUMBER_FORMAT.format(value);
        }
    };
    private static DecimalFormat DECIMAL_FORMAT = new DecimalFormat("########0.00");
    public static StatFormatter TIME_FORMATTER = new StatFormatter() {
        @Override
        public String format(int value) {
            double d0 = value / 20.0;
            double d1 = d0 / 60.0;
            double d2 = d1 / 60.0;
            double d3 = d2 / 24.0;
            double d4 = d3 / 365.0;
            if (d4 > 0.5) {
                return Stat.DECIMAL_FORMAT.format(d4) + " y";
            } else if (d3 > 0.5) {
                return Stat.DECIMAL_FORMAT.format(d3) + " d";
            } else if (d2 > 0.5) {
                return Stat.DECIMAL_FORMAT.format(d2) + " h";
            } else {
                return d1 > 0.5 ? Stat.DECIMAL_FORMAT.format(d1) + " m" : d0 + " s";
            }
        }
    };
    public static StatFormatter DISTANCE_FORMATTER = new StatFormatter() {
        @Override
        public String format(int value) {
            double d0 = value / 100.0;
            double d1 = d0 / 1000.0;
            if (d1 > 0.5) {
                return Stat.DECIMAL_FORMAT.format(d1) + " km";
            } else {
                return d0 > 0.5 ? Stat.DECIMAL_FORMAT.format(d0) + " m" : value + " cm";
            }
        }
    };
    public static StatFormatter DIVIDE_BY_TEN_FORMATTER = new StatFormatter() {
        @Override
        public String format(int value) {
            return Stat.DECIMAL_FORMAT.format(value * 0.1);
        }
    };

    public Stat(String key, Text name, StatFormatter formatter) {
        this.key = key;
        this.name = name;
        this.formatter = formatter;
        this.criterion = new StatCriterion(this);
        ScoreboardCriterion.BY_NAME.put(this.criterion.getName(), this.criterion);
    }

    public Stat(String key, Text name) {
        this(key, name, NUMBER_FORMATTER);
    }

    public Stat setLocal() {
        this.local = true;
        return this;
    }

    public Stat register() {
        if (Stats.BY_KEY.containsKey(this.key)) {
            throw new RuntimeException("Duplicate stat id: \"" + Stats.BY_KEY.get(this.key).name + "\" and \"" + this.name + "\" at id " + this.key);
        }

        Stats.ALL.add(this);
        Stats.BY_KEY.put(this.key, this);
        return this;
    }

    public boolean isAchievement() {
        return false;
    }

    public String format(int value) {
        return this.formatter.format(value);
    }

    public Text getDecoratedName() {
        Text text = this.name.copy();
        text.getStyle().setColor(Formatting.GRAY);
        text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ACHIEVEMENT, new LiteralText(this.key)));
        return text;
    }

    public Text getNameForChat() {
        Text text = this.getDecoratedName();
        Text text1 = new LiteralText("[").append(text).append("]");
        text1.setStyle(text.getStyle());
        return text1;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            Stat stat = (Stat)object;
            return this.key.equals(stat.key);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    @Override
    public String toString() {
        return "Stat{id="
            + this.key
            + ", nameId="
            + this.name
            + ", awardLocallyOnly="
            + this.local
            + ", formatter="
            + this.formatter
            + ", objectiveCriteria="
            + this.criterion
            + '}';
    }

    public ScoreboardCriterion getCriterion() {
        return this.criterion;
    }

    public Class<? extends StatProgress> getProgressType() {
        return this.progressType;
    }

    public Stat setProgressType(Class<? extends StatProgress> progressType) {
        this.progressType = progressType;
        return this;
    }
}
