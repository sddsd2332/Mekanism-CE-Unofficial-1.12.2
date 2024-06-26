// Made with Blockbench 4.10.3
// Exported for Minecraft version 1.7 - 1.12
// Paste this class into your mod and generate all required imports


public class model extends ModelBase {
	private final ModelRenderer player;
	private final ModelRenderer helmet_armor;
	private final ModelRenderer helmet_head_visor_upper_r1;
	private final ModelRenderer helmet_head_visor_lower_r1;
	private final ModelRenderer helmet_head_chin2_r1;
	private final ModelRenderer helmet_head_center3_r1;
	private final ModelRenderer helmet_head_center1_r1;
	private final ModelRenderer chest_armor;
	private final ModelRenderer chest_body_plate5_r1;
	private final ModelRenderer chest_body_plate3_r1;
	private final ModelRenderer chest_body_plate2_r1;
	private final ModelRenderer chest_body_plate1_r1;
	private final ModelRenderer chest_body_led2_r1;
	private final ModelRenderer chest_body_brace2_r1;
	private final ModelRenderer chest_body_brace1_r1;
	private final ModelRenderer chest_utilities;
	private final ModelRenderer chest_body_lower_plate2_r1;
	private final ModelRenderer chest_body_lower_plate1_r1;
	private final ModelRenderer chest_body_satchel3_r1;
	private final ModelRenderer chest_body_satchel2_r1;
	private final ModelRenderer chest_body_satchel1_r1;
	private final ModelRenderer back_exo;
	private final ModelRenderer chest_body_back_exo_brace_mid_r1;
	private final ModelRenderer shoulder_exo;
	private final ModelRenderer chest_body_shoulder_exo_brace_right_r1;
	private final ModelRenderer chest_body_shoulder_exo_brace_left_r1;
	private final ModelRenderer left_arm_armor;
	private final ModelRenderer chest_left_arm_exo_brace1_r1;
	private final ModelRenderer chest_left_arm_exo_brace2_r1;
	private final ModelRenderer chest_left_arm_exo_brace3_r1;
	private final ModelRenderer right_arm_armor;
	private final ModelRenderer chest_right_arm_exo_brace1_r1;
	private final ModelRenderer chest_right_arm_exo_brace2_r1;
	private final ModelRenderer chest_right_arm_exo_brace3_r1;
	private final ModelRenderer legs_chest_exo;
	private final ModelRenderer right_leg_armor;
	private final ModelRenderer leggings_right_leg_exo_brace3_r1;
	private final ModelRenderer leggings_right_leg_exo_frame1_r1;
	private final ModelRenderer right_boot_armor;
	private final ModelRenderer shared_boots_leggings_right_leg_exo_frame_r1;
	private final ModelRenderer shared_boots_leggings_right_leg_exo_plate_r1;
	private final ModelRenderer boots_right_leg_plate1_r1;
	private final ModelRenderer boots_right_leg_shin_plate_r1;
	private final ModelRenderer left_leg_armor;
	private final ModelRenderer leggings_left_leg_exo_brace3_r1;
	private final ModelRenderer leggings_left_leg_exo_frame1_r1;
	private final ModelRenderer left_boot_armor;
	private final ModelRenderer shared_boots_leggings_left_leg_exo_frame_r1;
	private final ModelRenderer shared_boots_leggings_left_leg_exo_plate_r1;
	private final ModelRenderer boots_left_leg_plate1_r1;
	private final ModelRenderer boots_left_leg_shin_plate_r1;

	public model() {
		textureWidth = 128;
		textureHeight = 128;

		player = new ModelRenderer(this);
		player.setRotationPoint(-8.0F, 16.0F, 8.0F);
		player.cubeList.add(new ModelBox(player, 0, 0, 4.0F, -15.5F, -10.005F, 8, 12, 4, 0.0F, false));
		player.cubeList.add(new ModelBox(player, 40, 41, 12.0F, -15.5F, -10.0F, 4, 12, 4, 0.0F, false));
		player.cubeList.add(new ModelBox(player, 16, 41, 0.0F, -15.5F, -10.0F, 4, 12, 4, 0.0F, false));
		player.cubeList.add(new ModelBox(player, 0, 35, 3.99F, -3.5F, -10.01F, 4, 12, 4, 0.0F, false));
		player.cubeList.add(new ModelBox(player, 28, 29, 7.99F, -3.5F, -10.01F, 4, 12, 4, 0.0F, false));

		helmet_armor = new ModelRenderer(this);
		helmet_armor.setRotationPoint(0.0F, -2.0F, 1.0F);
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 71, 73, -1.0F, -5.5F, -4.0F, 2, 1, 5, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 55, 0, -1.0F, -4.5F, 2.0F, 2, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 54, 19, 1.0F, -5.0F, -4.5F, 1, 2, 7, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 56, 40, -2.0F, -5.0F, -4.5F, 1, 2, 7, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 0, 53, -2.0F, -0.5F, 1.5F, 1, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 24, 39, 1.0F, -0.5F, 1.5F, 1, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 84, 81, 3.0F, -1.5F, -2.0F, 1, 2, 3, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 84, 76, -4.0F, -1.5F, -2.0F, 1, 2, 3, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 42, 10, -3.5F, 1.5F, -6.0F, 7, 1, 4, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 0, 20, -1.0F, 0.75F, -6.5F, 2, 2, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 19, 21, -3.0F, -4.5F, -5.0F, 6, 1, 7, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 44, 37, 1.0F, -4.5F, -6.0F, 2, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 19, 21, -3.0F, -4.5F, -6.0F, 2, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 34, 65, 1.0F, -3.5F, -3.0F, 3, 1, 5, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 47, 2, 3.0F, -2.5F, 1.0F, 1, 2, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 63, 19, -4.0F, -3.5F, -3.0F, 3, 1, 5, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 42, 10, -4.0F, -2.5F, 1.0F, 1, 2, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 74, 33, -3.0F, -3.5F, 2.0F, 6, 3, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 0, 16, -3.0F, -3.5F, -5.0F, 6, 5, 7, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 55, 19, 3.005F, -2.5F, -1.005F, 1, 1, 1, 0.0F, false));
		helmet_armor.cubeList.add(new ModelBox(helmet_armor, 0, 55, -3.995F, -2.5F, -1.005F, 1, 1, 1, 0.0F, false));

		helmet_head_visor_upper_r1 = new ModelRenderer(this);
		helmet_head_visor_upper_r1.setRotationPoint(-1.0F, 1.249F, -4.2677F);
		helmet_armor.addChild(helmet_head_visor_upper_r1);
		setRotationAngle(helmet_head_visor_upper_r1, -0.4363F, 0.0F, 0.0F);
		helmet_head_visor_upper_r1.cubeList.add(new ModelBox(helmet_head_visor_upper_r1, 79, 37, -1.975F, -4.0F, -3.0F, 5, 2, 1, 0.0F, false));

		helmet_head_visor_lower_r1 = new ModelRenderer(this);
		helmet_head_visor_lower_r1.setRotationPoint(-1.0F, 1.5F, -2.0F);
		helmet_armor.addChild(helmet_head_visor_lower_r1);
		setRotationAngle(helmet_head_visor_lower_r1, 0.1745F, 0.0F, 0.0F);
		helmet_head_visor_lower_r1.cubeList.add(new ModelBox(helmet_head_visor_lower_r1, 50, 67, -1.995F, -4.0F, -3.5F, 5, 4, 2, 0.0F, false));

		helmet_head_chin2_r1 = new ModelRenderer(this);
		helmet_head_chin2_r1.setRotationPoint(-1.0F, 2.1464F, 4.0104F);
		helmet_armor.addChild(helmet_head_chin2_r1);
		setRotationAngle(helmet_head_chin2_r1, 0.7854F, 0.0F, 0.0F);
		helmet_head_chin2_r1.cubeList.add(new ModelBox(helmet_head_chin2_r1, 55, 15, -2.5F, -5.0F, -4.5F, 7, 1, 3, 0.0F, false));

		helmet_head_center3_r1 = new ModelRenderer(this);
		helmet_head_center3_r1.setRotationPoint(-3.0F, 0.8076F, -2.1959F);
		helmet_armor.addChild(helmet_head_center3_r1);
		setRotationAngle(helmet_head_center3_r1, -0.6109F, 0.0F, 0.0F);
		helmet_head_center3_r1.cubeList.add(new ModelBox(helmet_head_center3_r1, 23, 29, 2.0F, -7.0F, -1.0F, 2, 1, 2, 0.0F, false));

		helmet_head_center1_r1 = new ModelRenderer(this);
		helmet_head_center1_r1.setRotationPoint(-3.0F, 2.5346F, -4.6669F);
		helmet_armor.addChild(helmet_head_center1_r1);
		setRotationAngle(helmet_head_center1_r1, 0.4363F, 0.0F, 0.0F);
		helmet_head_center1_r1.cubeList.add(new ModelBox(helmet_head_center1_r1, 54, 28, 2.0F, -7.0F, 2.0F, 2, 1, 2, 0.0F, false));

		chest_armor = new ModelRenderer(this);
		chest_armor.setRotationPoint(-2.0F, 5.0F, -2.0F);
		chest_armor.cubeList.add(new ModelBox(chest_armor, 64, 49, 0.0F, -5.55F, 0.0F, 4, 2, 4, 0.0F, false));
		chest_armor.cubeList.add(new ModelBox(chest_armor, 19, 18, -2.5F, -2.4F, 3.25F, 9, 2, 1, 0.0F, false));
		chest_armor.cubeList.add(new ModelBox(chest_armor, 19, 11, -2.5F, 2.5F, -0.5F, 9, 2, 5, 0.0F, false));

		chest_body_plate5_r1 = new ModelRenderer(this);
		chest_body_plate5_r1.setRotationPoint(0.0F, 0.0F, 0.0F);
		chest_armor.addChild(chest_body_plate5_r1);
		setRotationAngle(chest_body_plate5_r1, 0.1745F, 0.0F, 0.0F);
		chest_body_plate5_r1.cubeList.add(new ModelBox(chest_body_plate5_r1, 18, 65, 2.15F, -0.5F, -1.5F, 2, 3, 1, 0.0F, false));
		chest_body_plate5_r1.cubeList.add(new ModelBox(chest_body_plate5_r1, 45, 65, -0.15F, -0.5F, -1.5F, 2, 3, 1, 0.0F, false));

		chest_body_plate3_r1 = new ModelRenderer(this);
		chest_body_plate3_r1.setRotationPoint(2.0F, -1.3777F, -0.3473F);
		chest_armor.addChild(chest_body_plate3_r1);
		setRotationAngle(chest_body_plate3_r1, 0.1745F, 0.0F, 0.0F);
		chest_body_plate3_r1.cubeList.add(new ModelBox(chest_body_plate3_r1, 70, 6, -3.5F, 0.0F, -1.0F, 7, 2, 1, 0.0F, false));

		chest_body_plate2_r1 = new ModelRenderer(this);
		chest_body_plate2_r1.setRotationPoint(2.0F, -3.0F, 0.0F);
		chest_armor.addChild(chest_body_plate2_r1);
		setRotationAngle(chest_body_plate2_r1, -0.1745F, 0.0F, 0.0F);
		chest_body_plate2_r1.cubeList.add(new ModelBox(chest_body_plate2_r1, 12, 35, -3.5F, -1.0F, -1.0F, 7, 3, 1, 0.0F, false));

		chest_body_plate1_r1 = new ModelRenderer(this);
		chest_body_plate1_r1.setRotationPoint(1.0F, -2.0F, -1.0F);
		chest_armor.addChild(chest_body_plate1_r1);
		setRotationAngle(chest_body_plate1_r1, 0.0873F, 0.0F, 0.0F);
		chest_body_plate1_r1.cubeList.add(new ModelBox(chest_body_plate1_r1, 74, 55, -1.5F, -1.2F, -1.0F, 5, 3, 2, 0.0F, false));

		chest_body_led2_r1 = new ModelRenderer(this);
		chest_body_led2_r1.setRotationPoint(1.0F, -1.6F, -1.6F);
		chest_armor.addChild(chest_body_led2_r1);
		setRotationAngle(chest_body_led2_r1, -0.0873F, 0.0F, 0.0F);
		chest_body_led2_r1.cubeList.add(new ModelBox(chest_body_led2_r1, 56, 44, -0.5F, -0.5F, -0.47F, 1, 1, 1, 0.0F, false));
		chest_body_led2_r1.cubeList.add(new ModelBox(chest_body_led2_r1, 26, 59, 1.5F, -0.5F, -0.47F, 1, 1, 1, 0.0F, false));

		chest_body_brace2_r1 = new ModelRenderer(this);
		chest_body_brace2_r1.setRotationPoint(3.0F, -3.7452F, 0.3947F);
		chest_armor.addChild(chest_body_brace2_r1);
		setRotationAngle(chest_body_brace2_r1, 0.2618F, 0.0F, 0.0F);
		chest_body_brace2_r1.cubeList.add(new ModelBox(chest_body_brace2_r1, 24, 0, -5.5F, 3.0952F, -2.5F, 9, 1, 5, 0.0F, false));

		chest_body_brace1_r1 = new ModelRenderer(this);
		chest_body_brace1_r1.setRotationPoint(3.0F, -5.694F, 1.9982F);
		chest_armor.addChild(chest_body_brace1_r1);
		setRotationAngle(chest_body_brace1_r1, -0.1745F, 0.0F, 0.0F);
		chest_body_brace1_r1.cubeList.add(new ModelBox(chest_body_brace1_r1, 0, 29, -5.5F, 2.994F, -2.5982F, 9, 1, 5, 0.0F, false));

		chest_utilities = new ModelRenderer(this);
		chest_utilities.setRotationPoint(2.0F, 5.0F, 0.0F);
		chest_armor.addChild(chest_utilities);
		

		chest_body_lower_plate2_r1 = new ModelRenderer(this);
		chest_body_lower_plate2_r1.setRotationPoint(-1.0F, -1.0F, 0.0F);
		chest_utilities.addChild(chest_body_lower_plate2_r1);
		setRotationAngle(chest_body_lower_plate2_r1, -0.1745F, 0.0F, 0.0F);
		chest_body_lower_plate2_r1.cubeList.add(new ModelBox(chest_body_lower_plate2_r1, 79, 60, -1.5F, -1.0F, -1.0F, 5, 3, 1, 0.0F, false));

		chest_body_lower_plate1_r1 = new ModelRenderer(this);
		chest_body_lower_plate1_r1.setRotationPoint(-2.0F, -3.0F, -2.0F);
		chest_utilities.addChild(chest_body_lower_plate1_r1);
		setRotationAngle(chest_body_lower_plate1_r1, 0.1745F, 0.0F, 0.0F);
		chest_body_lower_plate1_r1.cubeList.add(new ModelBox(chest_body_lower_plate1_r1, 40, 29, 1.0F, 4.0F, 0.5F, 2, 2, 1, 0.0F, false));

		chest_body_satchel3_r1 = new ModelRenderer(this);
		chest_body_satchel3_r1.setRotationPoint(-1.9414F, -1.5891F, -0.8755F);
		chest_utilities.addChild(chest_body_satchel3_r1);
		setRotationAngle(chest_body_satchel3_r1, 0.0873F, 0.0873F, 0.0F);
		chest_body_satchel3_r1.cubeList.add(new ModelBox(chest_body_satchel3_r1, 0, 16, -1.5586F, -1.5F, -0.5F, 2, 3, 1, 0.0F, false));

		chest_body_satchel2_r1 = new ModelRenderer(this);
		chest_body_satchel2_r1.setRotationPoint(0.5377F, -2.0F, -2.042F);
		chest_utilities.addChild(chest_body_satchel2_r1);
		setRotationAngle(chest_body_satchel2_r1, 0.0873F, -0.0873F, 0.0F);
		chest_body_satchel2_r1.cubeList.add(new ModelBox(chest_body_satchel2_r1, 44, 33, 0.9623F, -1.0F, 0.5F, 2, 3, 1, 0.0F, false));

		chest_body_satchel1_r1 = new ModelRenderer(this);
		chest_body_satchel1_r1.setRotationPoint(-2.0F, -2.0F, -2.0F);
		chest_utilities.addChild(chest_body_satchel1_r1);
		setRotationAngle(chest_body_satchel1_r1, 0.0873F, 0.0F, 0.0F);
		chest_body_satchel1_r1.cubeList.add(new ModelBox(chest_body_satchel1_r1, 16, 74, 1.0F, -1.0F, 0.5F, 2, 3, 1, 0.0F, false));

		back_exo = new ModelRenderer(this);
		back_exo.setRotationPoint(2.0F, 0.8299F, 4.683F);
		chest_armor.addChild(back_exo);
		back_exo.cubeList.add(new ModelBox(back_exo, 0, 70, -1.0F, -6.3299F, -1.683F, 2, 11, 2, 0.0F, false));
		back_exo.cubeList.add(new ModelBox(back_exo, 38, 18, -3.5F, -5.3249F, -1.1792F, 7, 5, 3, 0.0F, false));
		back_exo.cubeList.add(new ModelBox(back_exo, 23, 69, -2.0F, -9.3299F, 0.817F, 4, 7, 2, 0.0F, false));

		chest_body_back_exo_brace_mid_r1 = new ModelRenderer(this);
		chest_body_back_exo_brace_mid_r1.setRotationPoint(0.0F, -0.8782F, -0.1155F);
		back_exo.addChild(chest_body_back_exo_brace_mid_r1);
		setRotationAngle(chest_body_back_exo_brace_mid_r1, -0.0873F, 0.0F, 0.0F);
		chest_body_back_exo_brace_mid_r1.cubeList.add(new ModelBox(chest_body_back_exo_brace_mid_r1, 65, 37, -2.5F, -3.5F, -1.0F, 5, 7, 2, 0.0F, false));

		shoulder_exo = new ModelRenderer(this);
		shoulder_exo.setRotationPoint(2.0F, -4.412F, 4.699F);
		chest_armor.addChild(shoulder_exo);
		

		chest_body_shoulder_exo_brace_right_r1 = new ModelRenderer(this);
		chest_body_shoulder_exo_brace_right_r1.setRotationPoint(-4.076F, 0.0F, 0.0F);
		shoulder_exo.addChild(chest_body_shoulder_exo_brace_right_r1);
		setRotationAngle(chest_body_shoulder_exo_brace_right_r1, 0.0F, -0.2618F, 0.0873F);
		chest_body_shoulder_exo_brace_right_r1.cubeList.add(new ModelBox(chest_body_shoulder_exo_brace_right_r1, 62, 0, -4.0F, -1.0F, -0.5F, 8, 2, 1, 0.0F, false));

		chest_body_shoulder_exo_brace_left_r1 = new ModelRenderer(this);
		chest_body_shoulder_exo_brace_left_r1.setRotationPoint(4.0541F, 0.0F, 0.0F);
		shoulder_exo.addChild(chest_body_shoulder_exo_brace_left_r1);
		setRotationAngle(chest_body_shoulder_exo_brace_left_r1, 0.0F, 0.2618F, -0.0873F);
		chest_body_shoulder_exo_brace_left_r1.cubeList.add(new ModelBox(chest_body_shoulder_exo_brace_left_r1, 70, 3, -4.0F, -1.0F, -0.5F, 8, 2, 1, 0.0F, false));

		left_arm_armor = new ModelRenderer(this);
		left_arm_armor.setRotationPoint(6.0F, 2.5F, 0.0F);
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 23, 32, 1.05F, 3.0F, -2.05F, 1, 1, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 56, 59, -0.3454F, -2.3145F, -2.5F, 3, 3, 5, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 8, 74, 0.5F, 5.5F, -2.5F, 2, 4, 4, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 9, 65, -2.5F, 1.5F, -2.505F, 2, 4, 5, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 67, 64, -0.5F, 1.5F, -2.75F, 1, 4, 5, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 74, 15, 0.5F, 2.25F, -2.5F, 2, 1, 5, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 57, 73, 0.5F, 3.75F, -2.5F, 2, 1, 5, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 62, 3, -0.5F, 9.5F, -2.5F, 3, 1, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 64, 67, -0.5F, 9.5F, -0.5F, 3, 1, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 53, 73, -0.5F, 9.5F, -1.5F, 3, 1, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 64, 55, -0.5F, 9.5F, 0.5F, 3, 1, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 0, 35, -2.5F, 7.5F, -1.5F, 1, 3, 1, 0.0F, false));
		left_arm_armor.cubeList.add(new ModelBox(left_arm_armor, 16, 41, -2.5F, 7.5F, -0.5F, 1, 3, 1, 0.0F, false));

		chest_left_arm_exo_brace1_r1 = new ModelRenderer(this);
		chest_left_arm_exo_brace1_r1.setRotationPoint(2.418F, 0.7177F, 0.995F);
		left_arm_armor.addChild(chest_left_arm_exo_brace1_r1);
		setRotationAngle(chest_left_arm_exo_brace1_r1, 0.0F, 0.0F, -0.0873F);
		chest_left_arm_exo_brace1_r1.cubeList.add(new ModelBox(chest_left_arm_exo_brace1_r1, 54, 79, -0.5F, -3.0F, -1.5F, 1, 6, 3, 0.0F, false));

		chest_left_arm_exo_brace2_r1 = new ModelRenderer(this);
		chest_left_arm_exo_brace2_r1.setRotationPoint(1.5F, 4.6772F, 2.1713F);
		left_arm_armor.addChild(chest_left_arm_exo_brace2_r1);
		setRotationAngle(chest_left_arm_exo_brace2_r1, 0.0873F, 0.0F, 0.0F);
		chest_left_arm_exo_brace2_r1.cubeList.add(new ModelBox(chest_left_arm_exo_brace2_r1, 79, 9, -1.5F, -1.0F, -1.5F, 3, 2, 3, 0.0F, false));

		chest_left_arm_exo_brace3_r1 = new ModelRenderer(this);
		chest_left_arm_exo_brace3_r1.setRotationPoint(1.55F, 7.5669F, 1.8402F);
		left_arm_armor.addChild(chest_left_arm_exo_brace3_r1);
		setRotationAngle(chest_left_arm_exo_brace3_r1, -0.2618F, 0.0F, 0.0F);
		chest_left_arm_exo_brace3_r1.cubeList.add(new ModelBox(chest_left_arm_exo_brace3_r1, 24, 83, -1.0F, -2.5F, -1.0F, 2, 5, 2, 0.0F, false));

		right_arm_armor = new ModelRenderer(this);
		right_arm_armor.setRotationPoint(-6.0F, 2.5F, 0.0F);
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 24, 3, -2.05F, 3.0F, -2.05F, 1, 1, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 40, 57, -2.7449F, -2.308F, -2.5F, 3, 3, 5, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 45, 73, -2.5F, 5.5F, -2.5F, 2, 4, 4, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 25, 60, 0.5F, 1.5F, -2.505F, 2, 4, 5, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 67, 55, -0.5F, 1.5F, -2.75F, 1, 4, 5, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 35, 71, -2.5F, 2.25F, -2.5F, 2, 1, 5, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 70, 9, -2.5F, 3.75F, -2.5F, 2, 1, 5, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 0, 51, -2.5F, 9.5F, -2.5F, 3, 1, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 47, 0, -2.5F, 9.5F, -0.5F, 3, 1, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 16, 39, -2.5F, 9.5F, -1.5F, 3, 1, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 38, 26, -2.5F, 9.5F, 0.5F, 3, 1, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 0, 28, 1.5F, 7.5F, -1.5F, 1, 3, 1, 0.0F, false));
		right_arm_armor.cubeList.add(new ModelBox(right_arm_armor, 0, 0, 1.5F, 7.5F, -0.5F, 1, 3, 1, 0.0F, false));

		chest_right_arm_exo_brace1_r1 = new ModelRenderer(this);
		chest_right_arm_exo_brace1_r1.setRotationPoint(-2.582F, 0.7177F, 0.995F);
		right_arm_armor.addChild(chest_right_arm_exo_brace1_r1);
		setRotationAngle(chest_right_arm_exo_brace1_r1, 0.0F, 0.0F, 0.0873F);
		chest_right_arm_exo_brace1_r1.cubeList.add(new ModelBox(chest_right_arm_exo_brace1_r1, 40, 78, -0.426F, -3.0F, -1.5F, 1, 6, 3, 0.0F, false));

		chest_right_arm_exo_brace2_r1 = new ModelRenderer(this);
		chest_right_arm_exo_brace2_r1.setRotationPoint(-1.5F, 4.6772F, 2.1713F);
		right_arm_armor.addChild(chest_right_arm_exo_brace2_r1);
		setRotationAngle(chest_right_arm_exo_brace2_r1, 0.0873F, 0.0F, 0.0F);
		chest_right_arm_exo_brace2_r1.cubeList.add(new ModelBox(chest_right_arm_exo_brace2_r1, 20, 78, -1.5F, -1.0F, -1.5F, 3, 2, 3, 0.0F, false));

		chest_right_arm_exo_brace3_r1 = new ModelRenderer(this);
		chest_right_arm_exo_brace3_r1.setRotationPoint(-1.45F, 7.5669F, 1.8402F);
		right_arm_armor.addChild(chest_right_arm_exo_brace3_r1);
		setRotationAngle(chest_right_arm_exo_brace3_r1, -0.2618F, 0.0F, 0.0F);
		chest_right_arm_exo_brace3_r1.cubeList.add(new ModelBox(chest_right_arm_exo_brace3_r1, 32, 45, -1.0F, -2.5F, -1.0F, 2, 5, 2, 0.0F, false));

		legs_chest_exo = new ModelRenderer(this);
		legs_chest_exo.setRotationPoint(0.0F, 11.5228F, 2.5229F);
		legs_chest_exo.cubeList.add(new ModelBox(legs_chest_exo, 24, 6, -5.0F, -2.0228F, -2.0191F, 10, 2, 2, 0.0F, false));
		legs_chest_exo.cubeList.add(new ModelBox(legs_chest_exo, 80, 51, -2.0F, -1.0F, -1.0F, 4, 2, 2, 0.0F, false));

		right_leg_armor = new ModelRenderer(this);
		right_leg_armor.setRotationPoint(-2.0F, 12.5F, 0.0F);
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 48, 2, -2.5F, 4.5F, -1.5F, 5, 4, 4, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 20, 0, -1.495F, 4.5F, -2.5F, 3, 2, 1, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 0, 61, -2.5F, -0.5F, -2.5F, 2, 4, 5, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 80, 69, -0.5F, -0.5F, -2.5F, 1, 2, 4, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 0, 32, -2.25F, 5.0F, -2.25F, 1, 1, 1, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 52, 40, -1.5F, 5.0F, 2.0F, 3, 2, 2, 0.0F, false));
		right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 74, 64, -3.0F, 4.5228F, 0.0F, 3, 2, 3, 0.0F, false));

		leggings_right_leg_exo_brace3_r1 = new ModelRenderer(this);
		leggings_right_leg_exo_brace3_r1.setRotationPoint(2.0F, 3.5F, 0.0F);
		right_leg_armor.addChild(leggings_right_leg_exo_brace3_r1);
		setRotationAngle(leggings_right_leg_exo_brace3_r1, 0.0873F, 0.0F, 0.0873F);
		leggings_right_leg_exo_brace3_r1.cubeList.add(new ModelBox(leggings_right_leg_exo_brace3_r1, 32, 77, -5.0F, -4.5F, -1.1F, 1, 6, 3, 0.0F, false));

		leggings_right_leg_exo_frame1_r1 = new ModelRenderer(this);
		leggings_right_leg_exo_frame1_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
		right_leg_armor.addChild(leggings_right_leg_exo_frame1_r1);
		setRotationAngle(leggings_right_leg_exo_frame1_r1, 0.0873F, 0.0F, 0.0F);
		leggings_right_leg_exo_frame1_r1.cubeList.add(new ModelBox(leggings_right_leg_exo_frame1_r1, 78, 79, -1.0F, -9.0F, -0.5F, 2, 9, 1, 0.0F, false));

		right_boot_armor = new ModelRenderer(this);
		right_boot_armor.setRotationPoint(-2.0F, 12.5F, 0.0F);
		right_boot_armor.cubeList.add(new ModelBox(right_boot_armor, 56, 49, -2.505F, 9.0F, -2.0F, 4, 1, 2, 0.0F, false));
		right_boot_armor.cubeList.add(new ModelBox(right_boot_armor, 8, 51, -2.0F, 10.5F, -3.5F, 3, 1, 1, 0.0F, false));
		right_boot_armor.cubeList.add(new ModelBox(right_boot_armor, 14, 57, -2.01F, 6.0F, -2.01F, 4, 4, 4, 0.0F, false));
		right_boot_armor.cubeList.add(new ModelBox(right_boot_armor, 27, 52, -1.99F, 10.008F, -2.05F, 4, 2, 5, 0.0F, false));
		right_boot_armor.cubeList.add(new ModelBox(right_boot_armor, 40, 26, -3.01F, 11.005F, -3.0F, 4, 1, 6, 0.0F, false));

		shared_boots_leggings_right_leg_exo_frame_r1 = new ModelRenderer(this);
		shared_boots_leggings_right_leg_exo_frame_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
		right_boot_armor.addChild(shared_boots_leggings_right_leg_exo_frame_r1);
		setRotationAngle(shared_boots_leggings_right_leg_exo_frame_r1, -0.0873F, 0.0F, 0.0F);
		shared_boots_leggings_right_leg_exo_frame_r1.cubeList.add(new ModelBox(shared_boots_leggings_right_leg_exo_frame_r1, 70, 79, -1.0F, 0.0F, -1.5F, 2, 6, 2, 0.0F, false));

		shared_boots_leggings_right_leg_exo_plate_r1 = new ModelRenderer(this);
		shared_boots_leggings_right_leg_exo_plate_r1.setRotationPoint(-5.0F, 3.5305F, 0.2618F);
		right_boot_armor.addChild(shared_boots_leggings_right_leg_exo_plate_r1);
		setRotationAngle(shared_boots_leggings_right_leg_exo_plate_r1, -0.0873F, 0.0F, 0.0F);
		shared_boots_leggings_right_leg_exo_plate_r1.cubeList.add(new ModelBox(shared_boots_leggings_right_leg_exo_plate_r1, 70, 25, 2.0F, 3.0038F, 0.0F, 3, 5, 3, 0.0F, false));

		boots_right_leg_plate1_r1 = new ModelRenderer(this);
		boots_right_leg_plate1_r1.setRotationPoint(-0.5F, 10.5F, -3.0F);
		right_boot_armor.addChild(boots_right_leg_plate1_r1);
		setRotationAngle(boots_right_leg_plate1_r1, 0.7854F, 0.0F, 0.0F);
		boots_right_leg_plate1_r1.cubeList.add(new ModelBox(boots_right_leg_plate1_r1, 47, 15, -2.005F, -0.5F, 0.0F, 4, 2, 1, 0.0F, false));

		boots_right_leg_shin_plate_r1 = new ModelRenderer(this);
		boots_right_leg_shin_plate_r1.setRotationPoint(0.0F, 8.5F, -1.5F);
		right_boot_armor.addChild(boots_right_leg_shin_plate_r1);
		setRotationAngle(boots_right_leg_shin_plate_r1, 0.1745F, 0.0F, 0.0F);
		boots_right_leg_shin_plate_r1.cubeList.add(new ModelBox(boots_right_leg_shin_plate_r1, 6, 82, -1.5F, -2.1F, -1.0F, 3, 4, 2, 0.0F, false));

		left_leg_armor = new ModelRenderer(this);
		left_leg_armor.setRotationPoint(2.0F, 12.5F, 0.0F);
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 0, 53, -2.5F, 4.5F, -1.5F, 5, 4, 4, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 51, 59, -1.495F, 4.5F, -2.5F, 3, 2, 1, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 61, 5, 0.5F, -0.5F, -2.5F, 2, 4, 5, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 83, 14, -0.5F, -0.5F, -2.5F, 1, 2, 4, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 59, 45, 1.25F, 5.0F, -2.25F, 1, 1, 1, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 66, 73, -1.5F, 5.0F, 2.0F, 3, 2, 2, 0.0F, false));
		left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 79, 21, 0.0F, 4.5228F, 0.0F, 3, 2, 3, 0.0F, false));

		leggings_left_leg_exo_brace3_r1 = new ModelRenderer(this);
		leggings_left_leg_exo_brace3_r1.setRotationPoint(-2.0F, 3.5F, 0.0F);
		left_leg_armor.addChild(leggings_left_leg_exo_brace3_r1);
		setRotationAngle(leggings_left_leg_exo_brace3_r1, 0.0873F, 0.0F, -0.0873F);
		leggings_left_leg_exo_brace3_r1.cubeList.add(new ModelBox(leggings_left_leg_exo_brace3_r1, 62, 79, 4.0F, -4.5F, -1.1F, 1, 6, 3, 0.0F, false));

		leggings_left_leg_exo_frame1_r1 = new ModelRenderer(this);
		leggings_left_leg_exo_frame1_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
		left_leg_armor.addChild(leggings_left_leg_exo_frame1_r1);
		setRotationAngle(leggings_left_leg_exo_frame1_r1, 0.0873F, 0.0F, 0.0F);
		leggings_left_leg_exo_frame1_r1.cubeList.add(new ModelBox(leggings_left_leg_exo_frame1_r1, 48, 81, -1.0F, -9.0F, -0.5F, 2, 9, 1, 0.0F, false));

		left_boot_armor = new ModelRenderer(this);
		left_boot_armor.setRotationPoint(2.0F, 12.5F, 0.0F);
		left_boot_armor.cubeList.add(new ModelBox(left_boot_armor, 79, 40, -1.505F, 9.0F, -2.005F, 4, 1, 2, 0.0F, false));
		left_boot_armor.cubeList.add(new ModelBox(left_boot_armor, 58, 37, -1.0F, 10.5F, -3.5F, 3, 1, 1, 0.0F, false));
		left_boot_armor.cubeList.add(new ModelBox(left_boot_armor, 58, 29, -2.01F, 6.0F, -2.01F, 4, 4, 4, 0.0F, false));
		left_boot_armor.cubeList.add(new ModelBox(left_boot_armor, 51, 52, -2.01F, 10.008F, -2.05F, 4, 2, 5, 0.0F, false));
		left_boot_armor.cubeList.add(new ModelBox(left_boot_armor, 44, 33, -1.01F, 11.005F, -3.0F, 4, 1, 6, 0.0F, false));

		shared_boots_leggings_left_leg_exo_frame_r1 = new ModelRenderer(this);
		shared_boots_leggings_left_leg_exo_frame_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
		left_boot_armor.addChild(shared_boots_leggings_left_leg_exo_frame_r1);
		setRotationAngle(shared_boots_leggings_left_leg_exo_frame_r1, -0.0873F, 0.0F, 0.0F);
		shared_boots_leggings_left_leg_exo_frame_r1.cubeList.add(new ModelBox(shared_boots_leggings_left_leg_exo_frame_r1, 16, 83, -1.0F, 0.0F, -1.5F, 2, 6, 2, 0.0F, false));

		shared_boots_leggings_left_leg_exo_plate_r1 = new ModelRenderer(this);
		shared_boots_leggings_left_leg_exo_plate_r1.setRotationPoint(-2.0F, 3.5305F, 0.2618F);
		left_boot_armor.addChild(shared_boots_leggings_left_leg_exo_plate_r1);
		setRotationAngle(shared_boots_leggings_left_leg_exo_plate_r1, -0.0873F, 0.0F, 0.0F);
		shared_boots_leggings_left_leg_exo_plate_r1.cubeList.add(new ModelBox(shared_boots_leggings_left_leg_exo_plate_r1, 76, 43, 2.0F, 3.0038F, 0.0F, 3, 5, 3, 0.0F, false));

		boots_left_leg_plate1_r1 = new ModelRenderer(this);
		boots_left_leg_plate1_r1.setRotationPoint(0.5F, 10.5F, -3.0F);
		left_boot_armor.addChild(boots_left_leg_plate1_r1);
		setRotationAngle(boots_left_leg_plate1_r1, 0.7854F, 0.0F, 0.0F);
		boots_left_leg_plate1_r1.cubeList.add(new ModelBox(boots_left_leg_plate1_r1, 34, 59, -2.005F, -0.5F, 0.0F, 4, 2, 1, 0.0F, false));

		boots_left_leg_shin_plate_r1 = new ModelRenderer(this);
		boots_left_leg_shin_plate_r1.setRotationPoint(0.0F, 8.5F, -1.5F);
		left_boot_armor.addChild(boots_left_leg_shin_plate_r1);
		setRotationAngle(boots_left_leg_shin_plate_r1, 0.1745F, 0.0F, 0.0F);
		boots_left_leg_shin_plate_r1.cubeList.add(new ModelBox(boots_left_leg_shin_plate_r1, 82, 26, -1.5F, -2.1F, -1.0F, 3, 4, 2, 0.0F, false));
	}

	@Override
	public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
		player.render(f5);
		helmet_armor.render(f5);
		chest_armor.render(f5);
		left_arm_armor.render(f5);
		right_arm_armor.render(f5);
		legs_chest_exo.render(f5);
		right_leg_armor.render(f5);
		right_boot_armor.render(f5);
		left_leg_armor.render(f5);
		left_boot_armor.render(f5);
	}

	public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}
}