package com.haz4j.swagger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.googlecode.jsonrpc4j.JsonRpcParam;
import com.googlecode.jsonrpc4j.JsonRpcService;
import com.haz4j.swagger.annotation.Api;
import com.haz4j.swagger.annotation.ApiOperation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.repository.ClassRepository;
import sun.reflect.generics.tree.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ReflectionUtils {

    private static Field methodSignature;
    private static Field classTypeSignaturePath;
    private static Field arrayTypeSignatureComponentType;
    private static Field fieldSignature;
    private static Field classGenericInfo;

    static {
        init();
    }

    @SneakyThrows
    public static void init() {
        log.debug("init");
        methodSignature = Method.class.getDeclaredField("signature");
        classTypeSignaturePath = ClassTypeSignature.class.getDeclaredField("path");
        arrayTypeSignatureComponentType = ArrayTypeSignature.class.getDeclaredField("componentType");
        fieldSignature = Field.class.getDeclaredField("signature");
        classGenericInfo = Class.class.getDeclaredField("genericInfo");
        methodSignature.setAccessible(true);
        classTypeSignaturePath.setAccessible(true);
        arrayTypeSignatureComponentType.setAccessible(true);
        fieldSignature.setAccessible(true);
        classGenericInfo.setAccessible(true);
    }

    public static String getJsonRpcParam(Parameter parameter) {
        log.debug("getJsonRpcParam: parameter - " + parameter);
        JsonRpcParam[] jsonRpcParams = parameter.getAnnotationsByType(JsonRpcParam.class);
        if (jsonRpcParams != null
                && jsonRpcParams.length > 0
                && jsonRpcParams[0] != null
                && !StringUtils.isEmpty(jsonRpcParams[0].value())
                ) {
            return jsonRpcParams[0].value();
        } else {
            return parameter.getName();
        }
    }

    public static String getJsonProperty(Field field) {
        log.debug("getJsonProperty: field - " + field);
        JsonProperty[] jsonProperties = field.getAnnotationsByType(JsonProperty.class);
        if (jsonProperties != null
                && jsonProperties.length > 0
                && jsonProperties[0] != null
                && !StringUtils.isEmpty(jsonProperties[0].value())) {
            return jsonProperties[0].value();
        }
        return field.getName();
    }

    @SneakyThrows
    public static Optional<Class> getRealType(Field field, @NotNull Map<String, String> genericTypeArgs) {
        log.debug("getRealType: field - " + field + ", genericTypeArgs - " + genericTypeArgs);
        // signature = TR;
        Optional<String> signature = ReflectionUtils.getSignature(field);

        return signature.map(s -> genericTypeArgs.get(signature.get()))
                .map(className -> {
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(realClass -> TypeUtils.getRawType(realClass, null));
    }

    @SneakyThrows
    public static Optional<String> getSignature(Field field) {
        log.debug("getSignature: field - " + field);
        String signature = (String) fieldSignature.get(field); //for "R" it will be "TR;"
        return Optional.ofNullable(signature).map(s -> signature.substring(1, signature.length() - 1));
    }

    public static String getDescription(Method method) {
        log.debug("getDescription: method - " + method);

        return Optional
                .ofNullable(method.getAnnotation(ApiOperation.class))
                .map(ApiOperation::value)
                .orElse("");
    }

    @SneakyThrows
    public static List<TypeWrapper> getTypeWrappers(Method method) {
        log.debug("getTypeWrappers: method - " + method);
        String signature = (String) methodSignature.get(method);
        if (signature == null) {
            return new ArrayList<>();
        }
        MethodTypeSignature methodTypeSignature = SignatureParser.make().parseMethodSig(signature);

        TypeSignature[] parameterTypes = methodTypeSignature.getParameterTypes();

        return Arrays.stream(parameterTypes)
                .map(p -> toTypeWrapper(p))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private static TypeWrapper toTypeWrapper(TypeSignature parameterType) {
        log.debug("toTypeWrapper: parameterType - " + parameterType);
        TypeWrapper typeWrapper = null;

        if (ArrayTypeSignature.class.isAssignableFrom(parameterType.getClass())) {
            ClassTypeSignature classTypeSignature = (ClassTypeSignature) arrayTypeSignatureComponentType.get(parameterType);

            //this is array, thus we call the same method with element as a parameter
            return toTypeWrapper(classTypeSignature);
        }

        ArrayList paths = (ArrayList) classTypeSignaturePath.get(parameterType);

        for (Object o : paths) {
            SimpleClassTypeSignature signature = (SimpleClassTypeSignature) o;
            TypeArgument[] typeArguments = signature.getTypeArguments();

            List<TypeWrapper> childs = Arrays.stream(typeArguments)
                    .map(ta -> toTypeWrapper((TypeSignature) ta))
                    .collect(Collectors.toList());

            typeWrapper = new TypeWrapper(signature.getName(), childs);
        }

        return typeWrapper;
    }

    public static String getPath(Class api) {
        log.debug("getPath: api" + api);

        JsonRpcService jsonRpcService = (JsonRpcService) api.getAnnotation(JsonRpcService.class);
        return Optional.ofNullable(jsonRpcService)
                .map(JsonRpcService::value)
                .map(path -> path.replace("//", "/"))
                .orElseThrow(() -> new RuntimeException("Class " + api + " is annotated with @Api " +
                        "and should be annotated @JsonRpcService(value) with non-empty value"));
    }

    @SneakyThrows
    public static List<TypeVariable<?>> getTypeParams(Class<?> type) {
        log.debug("getTypeParams: type - " + type);
        return Optional.ofNullable((ClassRepository) classGenericInfo.get(type))
                .map(classRepository -> classRepository.getTypeParameters())
                .map(tp -> Arrays.asList(tp))
                .orElse(new ArrayList<>());
    }

    public static Pair<String, String> getTag(Class api) {
        log.debug("getTag: api - " + api);
        Api apiAnnotation = (Api) api.getAnnotation(Api.class);
        if (apiAnnotation != null && apiAnnotation.tags().length > 0) {
            String tagName = apiAnnotation.tags()[0];
            String tagValue = apiAnnotation.value();
            return Pair.of(tagName, tagValue);
        }
        return Pair.of("default", "");
    }
}
