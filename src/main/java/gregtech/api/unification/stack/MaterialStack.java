package gregtech.api.unification.stack;

import gregtech.api.unification.material.Material;
import gregtech.api.util.SmallDigits;

import org.jetbrains.annotations.NotNull;

public class MaterialStack {

    public final Material material;
    public final long amount;

    public MaterialStack(Material material, long amount) {
        this.material = material;
        this.amount = amount;
    }

    public MaterialStack copy(long amount) {
        return new MaterialStack(material, amount);
    }

    public MaterialStack copy() {
        return new MaterialStack(material, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MaterialStack that = (MaterialStack) o;

        if (amount != that.amount) return false;
        return material.equals(that.material);
    }

    @Override
    public int hashCode() {
        return material.hashCode();
    }

    @NotNull
    public String toFormatted() {
        final String chemicalFormula = material.getChemicalFormula();

        StringBuilder builder = new StringBuilder(chemicalFormula.length());
        if (chemicalFormula.isEmpty()) {
            builder.append('?');
        } else if (material.getMaterialComponents().size() > 1) {
            builder.append('(');
            builder.append(chemicalFormula);
            builder.append(')');
        } else {
            builder.append(chemicalFormula);
        }
        if (amount > 1) {
            builder.append(SmallDigits.toSmallDownNumbers(String.valueOf(amount)));
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "MaterialStack{material=" + material + ", amount=" + amount + '}';
    }
}
