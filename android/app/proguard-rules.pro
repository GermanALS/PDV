# Retrofit reads the response type of a suspend function from the generic
# signature of its Continuation parameter. When a DTO is only referenced from
# that signature (e.g. a call whose result is ignored), R8 removes the class and
# rewrites the type to Object, and the converter fails at runtime with
# "Serializer for class 'Any' is not found" (PLAN.md Parte 40, verificacion del
# release: HealthResponseDto).
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.pdv.pos.data.remote.dto.**
