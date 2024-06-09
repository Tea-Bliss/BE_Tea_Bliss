package store.teabliss.ingredient.mapper;

import org.apache.ibatis.annotations.Mapper;
import store.teabliss.ingredient.entity.Ingredient;

import java.util.List;
import java.util.Optional;

@Mapper
public interface IngredientMapper {

    void createIngredient(Ingredient ingredient);

    List<Ingredient> findByIngredients();

}
