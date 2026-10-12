package net.minecraft.scoreboard.team;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.text.Formatting;

public class Team extends AbstractTeam {
    private final Scoreboard scoreboard;
    private final String name;
    private final Set<String> members = Sets.newHashSet();
    private String displayName;
    private String prefix = "";
    private String suffix = "";
    private boolean allowFriendlyFire = true;
    private boolean showFriendlyInvisibles = true;
    private AbstractTeam.Visibility nameTagVisibility = AbstractTeam.Visibility.ALWAYS;
    private AbstractTeam.Visibility deathMessageVisibility = AbstractTeam.Visibility.ALWAYS;
    private Formatting color = Formatting.RESET;

    public Team(Scoreboard scoreboard, String name) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.displayName = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        this.displayName = displayName;
        this.scoreboard.onTeamUpdated(this);
    }

    @Override
    public Collection<String> getMembers() {
        return this.members;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null");
        }

        this.prefix = prefix;
        this.scoreboard.onTeamUpdated(this);
    }

    public String getSuffix() {
        return this.suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
        this.scoreboard.onTeamUpdated(this);
    }

    @Override
    public String getMemberDisplayName(String member) {
        return this.getPrefix() + member + this.getSuffix();
    }

    public static String getMemberDisplayName(AbstractTeam team, String member) {
        return team == null ? member : team.getMemberDisplayName(member);
    }

    @Override
    public boolean allowFriendlyFire() {
        return this.allowFriendlyFire;
    }

    public void setAllowFriendlyFire(boolean allowFriendlyFire) {
        this.allowFriendlyFire = allowFriendlyFire;
        this.scoreboard.onTeamUpdated(this);
    }

    @Override
    public boolean showFriendlyInvisibles() {
        return this.showFriendlyInvisibles;
    }

    public void setShowFriendlyInvisibles(boolean showFriendlyInvisibles) {
        this.showFriendlyInvisibles = showFriendlyInvisibles;
        this.scoreboard.onTeamUpdated(this);
    }

    @Override
    public AbstractTeam.Visibility getNameTagVisibility() {
        return this.nameTagVisibility;
    }

    @Override
    public AbstractTeam.Visibility getDeathMessageVisibility() {
        return this.deathMessageVisibility;
    }

    public void setNameTagVisibility(AbstractTeam.Visibility visibility) {
        this.nameTagVisibility = visibility;
        this.scoreboard.onTeamUpdated(this);
    }

    public void setDeathMessageVisibility(AbstractTeam.Visibility visibility) {
        this.deathMessageVisibility = visibility;
        this.scoreboard.onTeamUpdated(this);
    }

    public int packFriendlyFlags() {
        int i = 0;
        if (this.allowFriendlyFire()) {
            i |= 1;
        }

        if (this.showFriendlyInvisibles()) {
            i |= 2;
        }

        return i;
    }

    public void unpackFriendlyFlags(int flags) {
        this.setAllowFriendlyFire((flags & 1) > 0);
        this.setShowFriendlyInvisibles((flags & 2) > 0);
    }

    public void setColor(Formatting color) {
        this.color = color;
    }

    public Formatting getColor() {
        return this.color;
    }
}
