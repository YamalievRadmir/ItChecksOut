package ru.sklad.app.util;

import ru.sklad.app.entity.Product;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Fills only missing values; existing classifications are preserved. */
public final class ProductAnimalClassifier {
    private ProductAnimalClassifier() {}
    private static boolean code(String name, int code) {
        return Pattern.compile("(?<![\\p{L}\\d])пк\\s*-?\\s*" + code + "(?!\\d)").matcher(name).find();
    }
    public static boolean fillMissing(Product product) {
        String name = Objects.toString(product.getName(), "").toLowerCase(Locale.ROOT)
                .replace('ё', 'е').replaceAll("[\\u2010-\\u2015\\u2212]", "-");
        String animal = null, age = null;
        if (code(name, 1) || Pattern.compile("кур[\\s-]*несуш").matcher(name).find()) {
            animal = "Куры"; age = "Взрослые";
        } else if (code(name, 6)) {
            animal = "Бройлеры"; age = "Рост";
        } else if (code(name, 5) || name.contains("бройлер")) {
            animal = "Бройлеры"; age = "Старт";
        } else if (name.contains("индейк") || name.contains("индеек")) animal = "Индейки";
        else if (name.contains("свин")) animal = "Свиньи";
        else if (name.contains("коров") || name.contains("крс")) animal = "Коровы";
        else if (name.contains("кролик")) animal = "Кролики";
        else if (name.contains("перепел")) animal = "Перепела";
        else if (Pattern.compile("(?<![\\p{L}])(?:пшениц|овес|ячмен|горох|кукуруз|отруб)").matcher(name).find()) {
            animal = "Универсальный"; age = "Универсальный";
        }
        boolean changed = false;
        if ((product.getAnimalType() == null || product.getAnimalType().isBlank()) && animal != null) {
            product.setAnimalType(animal); changed = true;
        }
        if ((product.getAge() == null || product.getAge().isBlank()) && age != null) {
            product.setAge(age); changed = true;
        }
        return changed;
    }
}
