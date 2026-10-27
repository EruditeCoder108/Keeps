# LiteRT-LM 0.18.0 native embedding code finds these DTOs by literal JVM name.
# Its base InputData is retained, but nested Text/Image/Audio were renamed by R8,
# causing nativeComputeEmbedding to abort with ClassNotFoundException on device.
# Keep only the JNI data boundary; the app and inference orchestration remain optimized.
-keep class com.google.ai.edge.litertlm.InputData { *; }
-keep class com.google.ai.edge.litertlm.InputData$* { *; }
-keep class com.google.ai.edge.litertlm.EmbeddingResponse { *; }
-keep class com.google.ai.edge.litertlm.BenchmarkInfo { *; }
-keep class com.google.ai.edge.litertlm.LiteRtLmJniException { *; }
