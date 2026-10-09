package net.minecraft.realms;

import java.util.Random;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;

public class RealmsMth {
    public static float sin(float x) {
        return MathHelper.sin(x);
    }

    public static double nextDouble(Random random, double min, double max) {
        return MathHelper.nextDouble(random, min, max);
    }

    public static int ceil(float x) {
        return MathHelper.ceil(x);
    }

    public static int floor(double x) {
        return MathHelper.floor(x);
    }

    public static int intFloorDiv(int a, int b) {
        return MathHelper.floorDiv(a, b);
    }

    public static float abs(float x) {
        return MathHelper.abs(x);
    }

    public static int clamp(int x, int min, int max) {
        return MathHelper.clamp(x, min, max);
    }

    public static double clampedLerp(double min, double max, double delta) {
        return MathHelper.clampedLerp(min, max, delta);
    }

    public static int ceil(double x) {
        return MathHelper.ceil(x);
    }

    public static boolean isEmpty(String s) {
        return StringUtils.isEmpty(s);
    }

    public static long lfloor(double x) {
        return MathHelper.lfloor(x);
    }

    public static float sqrt(double x) {
        return MathHelper.sqrt(x);
    }

    public static double clamp(double x, double min, double max) {
        return MathHelper.clamp(x, min, max);
    }

    public static int getInt(String s, int defaultInt) {
        return MathHelper.parseInt(s, defaultInt);
    }

    public static double getDouble(String s, double defaultValue) {
        return MathHelper.parseDouble(s, defaultValue);
    }

    public static int log2(int x) {
        return MathHelper.log2(x);
    }

    public static int absFloor(double x) {
        return MathHelper.abs(x);
    }

    public static int smallestEncompassingPowerOfTwo(int x) {
        return MathHelper.smallestEncompassingPowerOfTwo(x);
    }

    public static float sqrt(float x) {
        return MathHelper.sqrt(x);
    }

    public static float cos(float x) {
        return MathHelper.cos(x);
    }

    public static int getInt(String x, int defaultValue, int min) {
        return MathHelper.parseInt(x, defaultValue, min);
    }

    public static int fastFloor(double x) {
        return MathHelper.fastFloor(x);
    }

    public static double absMax(double a, double b) {
        return MathHelper.absMax(a, b);
    }

    public static float nextFloat(Random random, float min, float max) {
        return MathHelper.nextFloat(random, min, max);
    }

    public static double wrapDegrees(double degrees) {
        return MathHelper.wrapDegrees(degrees);
    }

    public static float wrapDegrees(float degrees) {
        return MathHelper.wrapDegrees(degrees);
    }

    public static float clamp(float x, float min, float max) {
        return MathHelper.clamp(x, min, max);
    }

    public static double getDouble(String s, double defaultValue, double min) {
        return MathHelper.parseDouble(s, defaultValue, min);
    }

    public static int roundUp(int x, int interval) {
        return MathHelper.roundUp(x, interval);
    }

    public static double average(long[] s) {
        return MathHelper.average(s);
    }

    public static int floor(float x) {
        return MathHelper.floor(x);
    }

    public static int abs(int x) {
        return MathHelper.abs(x);
    }

    public static int nextInt(Random random, int defaultValue, int min) {
        return MathHelper.nextInt(random, defaultValue, min);
    }
}
