package dev.theboysclash.hubeconomy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

public final class CoinSidebar {

    private static final String OBJECTIVE_NAME = "hubeconomy_coins";
    private static final String SPACER_KEY = "\u00a7r\u00a7r ";
    private static final String COINS_KEY = "\u00a7r\u00a76Coins";
    private static final String BALANCE_KEY = "\u00a7r\u00a7fcoins";

    private final EconomyStore economy;

    public CoinSidebar(HubEconomyPlugin plugin, EconomyStore economy) {
        this.economy = economy;
    }

    public void attach(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(
                OBJECTIVE_NAME, Criteria.DUMMY, Component.text("KyleTurski MC"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        setLine(objective, 3, SPACER_KEY, Component.empty());
        setLine(objective, 2, COINS_KEY, Component.text("Coins", NamedTextColor.GOLD));
        setBalanceLine(objective, player.getUniqueId());

        player.setScoreboard(board);
    }

    public void refresh(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            attach(player);
            return;
        }
        setBalanceLine(objective, playerId);
    }

    public void detach(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void setBalanceLine(Objective objective, UUID playerId) {
        long balance = economy.getBalance(playerId);
        setLine(objective, 1, BALANCE_KEY, Component.text(MoneyFormat.format(balance), NamedTextColor.WHITE));
    }

    private static void setLine(Objective objective, int score, String entryKey, Component display) {
        Score line = objective.getScore(entryKey);
        line.setScore(score);
        line.customName(display);
    }
}
