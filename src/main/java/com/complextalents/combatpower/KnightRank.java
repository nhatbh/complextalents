package com.complextalents.combatpower;

import net.minecraft.ChatFormatting;

public enum KnightRank {
    INITIATE("Initiate", "✧", "\u00A77", ChatFormatting.GRAY, 0, 199),
    ARMIGER("Armiger", "✦", "\u00A7f", ChatFormatting.WHITE, 200, 499),
    CAVALIER("Cavalier", "⚔", "\u00A7a", ChatFormatting.GREEN, 500, 999),
    PALADIN("Paladin", "⛨", "\u00A7b", ChatFormatting.AQUA, 1000, 1999),
    TEMPLAR("Templar", "✠", "\u00A79", ChatFormatting.BLUE, 2000, 3999),
    SOVEREIGN("Sovereign", "✹", "\u00A75", ChatFormatting.DARK_PURPLE, 4000, 6499),
    
    // Divine Endgame Ranks (6,500+ CP)
    EXALTED("Exalted", "⚜", "\u00A76\u00A7l", ChatFormatting.GOLD, 6500, 9999),
    ASCENDANT("Ascendant", "✵", "\u00A7b\u00A7l", ChatFormatting.AQUA, 10000, 14999),
    ETHEREAL("Ethereal", "✟", "\u00A7d\u00A7l", ChatFormatting.LIGHT_PURPLE, 15000, 24999),
    EMPYREAN("Empyrean", "☥", "\u00A7e\u00A7l", ChatFormatting.YELLOW, 25000, Integer.MAX_VALUE);

    private final String baseTitle;
    private final String symbol;
    private final String colorCode;
    private final ChatFormatting color;
    private final int minCP;
    private final int maxCP;

    KnightRank(String baseTitle, String symbol, String colorCode, ChatFormatting color, int minCP, int maxCP) {
        this.baseTitle = baseTitle;
        this.symbol = symbol;
        this.colorCode = colorCode;
        this.color = color;
        this.minCP = minCP;
        this.maxCP = maxCP;
    }

    public String getBaseTitle() {
        return baseTitle;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getColorCode() {
        return colorCode;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public int getMinCP() {
        return minCP;
    }

    public int getMaxCP() {
        return maxCP;
    }

    public String getFormattedSymbol() {
        return colorCode + symbol + "\u00A7r";
    }

    public String getTitleForCP(int cp) {
        if (this == EXALTED) {
            // 6,500 - 7,499 = I, 7,500 - 8,499 = II, 8,500+ = III
            int tier = 1 + (cp - 6500) / 1000;
            return baseTitle + " " + toRoman(Math.min(tier, 3));
        } else if (this == ASCENDANT) {
            // 10,000 - 11,499 = I, 11,500 - 12,999 = II, 13,000+ = III
            int tier = 1 + (cp - 10000) / 1500;
            return baseTitle + " " + toRoman(Math.min(tier, 3));
        } else if (this == ETHEREAL) {
            // 15,000 - 17,999 = I, 18,000 - 20,999 = II, 21,000+ = III
            int tier = 1 + (cp - 15000) / 3000;
            return baseTitle + " " + toRoman(Math.min(tier, 3));
        } else if (this == EMPYREAN) {
            // 25,000+ = I, II, III... scaling every 5,000 CP
            int tier = 1 + (cp - 25000) / 5000;
            return baseTitle + " " + toRoman(Math.max(1, tier));
        }
        return baseTitle;
    }

    public static String toRoman(int number) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                number -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }

    public static KnightRank fromCP(int cp) {
        for (KnightRank rank : values()) {
            if (cp >= rank.minCP && cp <= rank.maxCP) {
                return rank;
            }
        }
        return EMPYREAN;
    }
}
