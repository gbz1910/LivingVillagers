# Método correto para cabeça de Villager — Living Villagers

Este é o método que finalmente funcionou para profissões customizadas sem quebrar a cabeça do villager.

## Regra principal

NUNCA use `super.hatVisible(false)` para esconder o chapéu de uma profissão customizada.

Esse método pode acabar ocultando mais partes do modelo do que o desejado, inclusive a própria cabeça.

## Solução correta

Use um `VillagerModel` customizado e controle diretamente as partes:

- `head` deve permanecer sempre visível;
- `hat` deve ser ocultado;
- `hat_rim` deve ser ocultado.

Exemplo usado na alpha.17:

```java
public final class MinerAwareVillagerModel extends VillagerModel<Villager> {
    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart hatRim;
    private boolean forceNoHat;

    public MinerAwareVillagerModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.hat = this.head.getChild("hat");
        this.hatRim = this.hat.getChild("hat_rim");
    }

    public void setForceNoHat(boolean forceNoHat) {
        this.forceNoHat = forceNoHat;
        applyHatState(true);
    }

    private void applyHatState(boolean requestedVisible) {
        this.head.visible = true;

        if (forceNoHat) {
            this.hat.visible = false;
            this.hatRim.visible = false;
        } else {
            this.hat.visible = requestedVisible;
            this.hatRim.visible = requestedVisible;
        }
    }

    @Override
    public void hatVisible(boolean visible) {
        applyHatState(visible);
    }
}
```

## Renderer

O renderer customizado deve ativar `forceNoHat` apenas para a profissão desejada e restaurar ao final do render.

```java
boolean miner = villager.getVillagerData().getProfession() == ModVillagerProfessions.MINER.get();

livingModel.setForceNoHat(miner);

try {
    super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
} finally {
    livingModel.setForceNoHat(false);
}
```

## Não fazer

- Não pintar a cabeça inteira de preto.
- Não usar capacete 3D para esconder o bug.
- Não tentar resolver apenas pelo `.png.mcmeta`.
- Não chamar `super.hatVisible(false)` para a profissão.
- Não substituir a cabeça vanilla por uma textura completa na camada de profissão.

## Padrão para próximas profissões

Se uma profissão não deve ter chapéu:

1. usar a cabeça vanilla;
2. manter `head.visible = true`;
3. definir `hat.visible = false`;
4. definir `hatRim.visible = false`;
5. aplicar isso somente durante o render daquela profissão.

Este é o padrão aprovado a partir da alpha.17.
