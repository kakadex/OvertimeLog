# R8 keep 规则基线（release 变体）。
#
# 原则：只保留"运行时会按名字查找"的东西，其余交给 R8 自动裁剪，
# 这样既能压体积，又不会出现"编译通过、运行时 ClassNotFound"。
#
# 当前项目没有反射边界：Room / Compose / Navigation / DataStore 都是编译期生成或直接调用，
# 所以下面只保留通用属性，不做大范围 keep。
#
# 以后加下面这些功能时，必须同步补规则，并把压缩包的冒烟验证跑一遍：
#   1. kotlinx.serialization（备份 JSON / 快照反序列化）
#      -keepattributes *Annotation*, InnerClasses
#      -keepclassmembers class com.overtimelog.** { *** Companion; }
#      -keepclasseswithmembers class com.overtimelog.** { kotlinx.serialization.KSerializer serializer(...); }
#   2. Hilt / Dagger（反射注入）
#      Hilt 自带 consumer 规则，但自定义 @EntryPoint 需要确认；
#   3. 任何 Class.forName / 反射读字段名的代码
#      -keep class <被反射的类> { *; }
#
# 通用属性：序列化、注解、内部类信息在运行时被反射读取的概率最高，保留成本很低。
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# 行号保留：崩溃堆栈能定位到具体行，代价是几十 KB，换排障效率值得。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 关闭部分激进优化，优先稳定性：
# 不要重写枚举的 values()/valueOf()（有些序列化/测试框架依赖）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable 的 CREATOR（Android 系统在跨进程时按字段名找）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
