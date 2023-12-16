package utils;

import transformer.HandlerEnum;

public class EnumUtil {
    public static HandlerEnum getEnumObjectForAOP(Object value) {
        for (HandlerEnum handlerEnum : HandlerEnum.values()) {
            if (handlerEnum.getValue().equals(value)) {
                return handlerEnum;
            }
        }
        return null;
    }
}
