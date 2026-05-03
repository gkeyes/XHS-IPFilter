package com.xposed.ipfilter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据层过滤器（生存版 v4.0.2）
 *
 * 与旧版 v3.x 的核心区别：
 * - ❌ 不做 View 层操作（无 setText hook，无 setVisibility）
 * - ✅ 只在数据层删 item + 同步 ne8.c 展开计数
 * - ✅ 简化逻辑：删父评论 → 删孤儿子评论 → 清理子评论列表 → 同步 ne8.c
 */
public class DataLayerFilter {

    private static final String COMMENT_INFO_CLASS = "com.xingin.entities.CommentCommentInfo";

    private static Field sIpLocationField = null;
    private static Field sIdField = null;
    private static Field sRootCommentIdField = null;
    private static Field sSubCommentsField = null;
    private static Field sSubCommentCountField = null;
    private static boolean sFieldsResolved = false;

    // ne8.c (展开/收起) 相关字段
    private static Field sNe8cCommentIdField = null;
    private static Field sNe8cCommentNumberField = null;
    private static Field sNe8cInitialCommentNumberField = null;
    private static Field sNe8cInitialSubCommentCountField = null;

    private DataLayerFilter() {}

    public static void resolveFields(ClassLoader cl) {
        try {
            Class<?> commentInfoClass = cl.loadClass(COMMENT_INFO_CLASS);
            sIpLocationField = commentInfoClass.getDeclaredField("ipLocation");
            sIpLocationField.setAccessible(true);

            sIdField = commentInfoClass.getDeclaredField("id");
            sIdField.setAccessible(true);

            sRootCommentIdField = commentInfoClass.getDeclaredField("rootCommentId");
            sRootCommentIdField.setAccessible(true);

            sSubCommentsField = commentInfoClass.getDeclaredField("subComments");
            sSubCommentsField.setAccessible(true);

            sSubCommentCountField = commentInfoClass.getDeclaredField("subCommentCount");
            sSubCommentCountField.setAccessible(true);

            sFieldsResolved = true;
            LogWriter.i("DataLayer: fields resolved ✓");
        } catch (Throwable t) {
            LogWriter.e("DataLayer: Failed to resolve fields", t);
        }
    }

    public static int filter(List<?> items, PrefManager prefManager) {
        if (!sFieldsResolved || sIpLocationField == null) return 0;

        Set<String> whitelist = prefManager.getWhitelist();
        if (whitelist.isEmpty()) return 0;

        int removed = 0;
        Set<String> removedParentIds = new HashSet<>();

        // ===== 第一轮：移除非白名单父评论 =====
        Iterator<?> it = items.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) continue;

            Object info = findCommentInfo(item);
            if (info == null) continue;

            String ipLocation = getIpLocation(info);
            String id = getId(info);

            if (ipLocation != null && !ipLocation.isEmpty()
                    && !isInWhitelist(ipLocation, whitelist)) {
                it.remove();
                removed++;
                if (id != null) {
                    removedParentIds.add(id);
                }
            }
        }

        // ===== 第二轮：移除孤儿子评论 + 清理与已移除父评论关联的 ne8.c =====
        if (!removedParentIds.isEmpty()) {
            int orphanRemoved = 0;
            it = items.iterator();
            while (it.hasNext()) {
                Object item = it.next();
                if (item == null) continue;
                // 清理 ne8.c
                if (isNe8cItem(item)) {
                    resolveNe8cFields(item);
                    String ne8Id = getNe8cCommentId(item);
                    if (ne8Id != null && removedParentIds.contains(ne8Id)) {
                        it.remove();
                        continue;
                    }
                }
                // 清理孤儿子评论
                Object info = findCommentInfo(item);
                if (info == null) continue;
                String rootId = getRootCommentId(info);
                if (rootId != null && removedParentIds.contains(rootId)) {
                    it.remove();
                    removed++;
                    orphanRemoved++;
                }
            }
            if (orphanRemoved > 0) {
                LogWriter.i("DataLayer: removed " + orphanRemoved + " orphan sub-comments");
            }
        }

        // ===== 第三轮：清理白名单父评论的子评论列表 + 记录新计数 =====
        Map<String, Integer> parentNewCounts = new HashMap<>();
        for (Object item : items) {
            if (item == null) continue;
            Object info = findCommentInfo(item);
            if (info == null) continue;

            String ipLocation = getIpLocation(info);
            if (ipLocation == null || ipLocation.isEmpty()) continue;
            if (!isInWhitelist(ipLocation, whitelist)) continue;

            int newCount = cleanSubComments(info, whitelist);
            if (newCount >= 0) {
                String parentId = getId(info);
                if (parentId != null) {
                    parentNewCounts.put(parentId, newCount);
                }
            }
        }

        // ===== 第四轮：同步 ne8.c "展开X条回复" =====
        if (!parentNewCounts.isEmpty()) {
            syncNe8c(items, parentNewCounts);
        }

        if (removed > 0) {
            LogWriter.i("DataLayer: filtered " + removed + " items, " + items.size() + " remain");
        }
        return removed;
    }

    // ========== 字段访问器 ==========

    private static Object findCommentInfo(Object itemObj) {
        try {
            Class<?> commentInfoClass = sIpLocationField != null ? sIpLocationField.getDeclaringClass() : null;
            if (commentInfoClass != null && commentInfoClass.isInstance(itemObj)) {
                return itemObj;
            }
            Class<?> cursor = itemObj.getClass();
            while (cursor != null && cursor != Object.class) {
                for (Field f : cursor.getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(itemObj);
                        if (val != null && commentInfoClass != null && commentInfoClass.isInstance(val)) {
                            return val;
                        }
                    } catch (Exception ignored) {}
                }
                cursor = cursor.getSuperclass();
            }
        } catch (Throwable t) {}
        return null;
    }

    private static String getIpLocation(Object info) {
        try { return (String) sIpLocationField.get(info); } catch (Exception e) { return null; }
    }

    private static String getId(Object info) {
        try { return (String) sIdField.get(info); } catch (Exception e) { return null; }
    }

    private static String getRootCommentId(Object info) {
        try { return (String) sRootCommentIdField.get(info); } catch (Exception e) { return null; }
    }

    /** 清理子评论列表，返回新的 subCommentCount，-1 表示没改动 */
    private static int cleanSubComments(Object info, Set<String> whitelist) {
        try {
            Object subList = sSubCommentsField.get(info);
            if (!(subList instanceof List)) return -1;
            List<?> subs = (List<?>) subList;
            if (subs.isEmpty()) return -1;

            int removed = 0;
            Iterator<?> it = subs.iterator();
            while (it.hasNext()) {
                Object sub = it.next();
                if (sub == null) continue;
                String subIp = getIpLocation(sub);
                if (subIp != null && !subIp.isEmpty() && !isInWhitelist(subIp, whitelist)) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                Object countObj = sSubCommentCountField.get(info);
                if (countObj instanceof Integer) {
                    int newCount = Math.max(0, (Integer) countObj - removed);
                    sSubCommentCountField.set(info, newCount);
                    return newCount;
                }
            }
        } catch (Throwable t) {}
        return -1;
    }

    // ========== ne8.c 同步 ==========

    private static void resolveNe8cFields(Object ne8cItem) {
        if (sNe8cCommentIdField != null) return;
        try {
            Class<?> clz = ne8cItem.getClass();
            sNe8cCommentIdField = clz.getDeclaredField("commentId");
            sNe8cCommentIdField.setAccessible(true);
            sNe8cCommentNumberField = clz.getDeclaredField("commentNumber");
            sNe8cCommentNumberField.setAccessible(true);
            sNe8cInitialCommentNumberField = clz.getDeclaredField("initialCommentNumber");
            sNe8cInitialCommentNumberField.setAccessible(true);
            sNe8cInitialSubCommentCountField = clz.getDeclaredField("initialSubCommentCount");
            sNe8cInitialSubCommentCountField.setAccessible(true);
            LogWriter.i("DataLayer: ne8.c fields resolved ✓");
        } catch (Throwable t) {
            LogWriter.e("DataLayer: Failed to resolve ne8.c fields", t);
        }
    }

    private static boolean isNe8cItem(Object item) {
        String name = item.getClass().getName();
        return name.contains("ne8") && item.getClass().getSimpleName().equals("c");
    }

    private static String getNe8cCommentId(Object item) {
        try { return (String) sNe8cCommentIdField.get(item); } catch (Exception e) { return null; }
    }

    /** 同步 ne8.c 的计数：subCount=0 则删除，否则更新数字 */
    private static void syncNe8c(List<?> items, Map<String, Integer> countMap) {
        int removed = 0, updated = 0;
        Iterator<?> it = items.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null || !isNe8cItem(item)) continue;
            resolveNe8cFields(item);
            if (sNe8cCommentIdField == null) continue;
            try {
                String commentId = (String) sNe8cCommentIdField.get(item);
                if (commentId == null) continue;
                Integer newCount = countMap.get(commentId);
                if (newCount == null) continue;

                if (newCount <= 0) {
                    it.remove();
                    removed++;
                } else {
                    Object oldNum = sNe8cCommentNumberField.get(item);
                    sNe8cCommentNumberField.set(item, newCount);
                    sNe8cInitialCommentNumberField.set(item, newCount);
                    sNe8cInitialSubCommentCountField.set(item, newCount);
                    updated++;
                    LogWriter.d("DataLayer: updated ne8.c " + oldNum + "→" + newCount);
                }
            } catch (Exception ignored) {}
        }
        if (removed > 0 || updated > 0) {
            LogWriter.i("DataLayer: ne8.c sync — removed " + removed + ", updated " + updated);
        }
    }

    private static boolean isInWhitelist(String ipLocation, Set<String> whitelist) {
        synchronized (whitelist) {
            for (String w : whitelist) {
                if (ipLocation.contains(w) || w.contains(ipLocation)) {
                    return true;
                }
            }
        }
        return false;
    }
}