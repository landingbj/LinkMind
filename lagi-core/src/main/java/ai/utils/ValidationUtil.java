//package ai.utils;
//
//import org.apache.commons.lang3.StringUtils;
//
//import javax.validation.ConstraintViolation;
//import javax.validation.Validation;
//import javax.validation.Validator;
//import java.util.List;
//import java.util.Set;
//
//public class ValidationUtil {
//
//    public final static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
//
//
//    /**
//     * 验证单个实体
//     *
//     * @param t   参数
//     * @param <T> 类型
//     * @return 验证结果
//     */
//    public static <T> String validateOne(T t) {
//        Set<ConstraintViolation<T>> validateResult = validator.validate(t);
//        if (validateResult != null && validateResult.size() != 0) {
//            StringBuilder sb = new StringBuilder();
//            for (ConstraintViolation<T> valid : validateResult) {
//                sb.append(valid.getPropertyPath().toString()).append(StringUtils.SPACE).append(valid.getMessage())
//                        .append(",");
//            }
//            return sb.toString();
//        }
//
//        return StringUtils.EMPTY;
//    }
//
//    /**
//     * 验证多个实体
//     *
//     * @param ts  参数
//     * @param <T> 类型
//     * @return 验证结果
//     */
//    public static <T> String validateMutil(List<T> ts) {
//        StringBuilder sb = new StringBuilder();
//        for (int index = 0; index < ts.size(); ++index) {
//            String validateOneResult = validateOne(ts.get(index));
//            if (!StringUtils.isBlank(validateOneResult)) {
//                sb.append("index[" + index + "]:").append(validateOneResult).append(";");
//            }
//        }
//
//        return sb.toString();
//    }
//}