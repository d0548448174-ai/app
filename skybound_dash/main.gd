extends Node3D

const SPEED := 9.0
const JUMP := 12.5
const GRAVITY := 30.0
const LIMIT_X := 5.2
const GOAL_Z := -132.0

var player: CharacterBody3D
var camera: Camera3D
var coins: Array[Node3D] = []
var enemies: Array[Node3D] = []
var obstacles: Array[Node3D] = []
var score := 0
var coin_count := 0
var t := 0.0
var ended := false
var won := false
var left_on := false
var right_on := false
var jump_request := false

var score_label: Label
var coin_label: Label
var title_label: Label
var restart: Button
var left_btn: Button
var right_btn: Button
var jump_btn: Button

func _ready():
	_setup_environment()
	_build_level()
	_build_player()
	_build_camera()
	_build_ui()

func _process(delta):
	t += delta
	for c in coins:
		if is_instance_valid(c):
			c.rotate_y(delta * 5.0)
			c.position.y = c.get_meta("y") + sin(t * 3.0 + c.position.z) * 0.12
	for e in enemies:
		if is_instance_valid(e):
			var bx = e.get_meta("bx")
			var phase = e.get_meta("phase")
			e.position.x = bx + sin(t * 2.1 + phase) * 1.5
			e.rotate_y(delta * 3.0)
	if is_instance_valid(player) and not ended and player.position.z <= GOAL_Z:
		_win()
	if is_instance_valid(player):
		var target = Vector3(player.position.x * 0.55, player.position.y + 5.4, player.position.z + 15.0)
		camera.position = camera.position.lerp(target, min(1.0, delta * 5.5))
		camera.look_at(Vector3(player.position.x * 0.25, player.position.y + 1.0, player.position.z - 8.0), Vector3.UP)

func _physics_process(delta):
	if ended or not is_instance_valid(player):
		return
	var x := 0.0
	if Input.is_action_pressed("move_left") or left_on:
		x -= 1.0
	if Input.is_action_pressed("move_right") or right_on:
		x += 1.0
	player.velocity.x = move_toward(player.velocity.x, x * 7.0, delta * 38.0)
	player.velocity.z = -SPEED
	if not player.is_on_floor():
		player.velocity.y -= GRAVITY * delta
	elif Input.is_action_just_pressed("jump") or jump_request:
		player.velocity.y = JUMP
		jump_request = false
	player.position.x = clamp(player.position.x, -LIMIT_X, LIMIT_X)
	player.move_and_slide()
	if player.position.y < -5.0:
		_lose("FELL!")
		return
	_collect_coins()
	_hit_enemies()
	_hit_obstacles()
	score = max(score, int(-player.position.z * 12.0) + coin_count * 100)
	score_label.text = "SCORE  %06d" % score

func _setup_environment():
	var we = WorldEnvironment.new()
	var env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color("#09132c")
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("#b9c7ff")
	env.ambient_light_energy = 0.9
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	we.environment = env
	add_child(we)

	var sun = DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52, -28, 0)
	sun.light_energy = 1.5
	sun.shadow_enabled = true
	add_child(sun)

func _build_level():
	_make_box(Vector3(0,-1.0,-65), Vector3(15,1,160), Color("#263b67"))
	for i in range(22):
		var z = 5.0 - i * 6.2
		var x = float((i % 3) - 1) * 3.2
		var y = 0.0 + float(i % 2) * 0.22
		_make_platform(Vector3(x,y,z), Vector3(5.8,0.65,5.2), i % 3)

	for i in range(10):
		var z = -8.0 - i * 12.5
		var x = float(((i * 2) % 5) - 2) * 1.7
		_make_obstacle(Vector3(x,0.9,z), Color("#ff5277"))
		if i % 2 == 0:
			_make_coin(Vector3(-x * 0.35, 2.1, z - 2.4))
		else:
			_make_coin(Vector3(x * 0.35, 2.1, z - 2.4))

	for i in range(7):
		var z = -20.0 - i * 15.0
		var x = float((i % 2) * 2 - 1) * 2.5
		_make_enemy(Vector3(x,0.75,z), float(i) * 0.8)

	_make_goal()

	for i in range(35):
		var star = MeshInstance3D.new()
		var sm = SphereMesh.new()
		sm.radius = 0.055
		sm.height = 0.11
		star.mesh = sm
		star.position = Vector3(float((i*17)%34)-17.0, 2.5+float((i*7)%10)*0.5, -5.0-float((i*13)%145))
		star.material_override = _mat(Color("#d8f8ff"), 0, 0.6)
		add_child(star)

func _build_player():
	player = CharacterBody3D.new()
	player.position = Vector3(0, 1.15, 10)
	add_child(player)

	var cs = CollisionShape3D.new()
	var capsule = CapsuleShape3D.new()
	capsule.radius = 0.48
	capsule.height = 1.65
	cs.shape = capsule
	player.add_child(cs)

	var body = MeshInstance3D.new()
	var cm = CapsuleMesh.new()
	cm.radius = 0.48
	cm.height = 1.65
	body.mesh = cm
	body.material_override = _mat(Color("#8b5cf6"), 0.05, 0.18)
	player.add_child(body)

	var head = MeshInstance3D.new()
	var sm = SphereMesh.new()
	sm.radius = 0.46
	sm.height = 0.92
	head.mesh = sm
	head.position.y = 0.72
	head.material_override = _mat(Color("#c4b5fd"), 0.03, 0.16)
	player.add_child(head)

	for px in [-0.16,0.16]:
		var eye = MeshInstance3D.new()
		var em = SphereMesh.new()
		em.radius = 0.065
		em.height = 0.13
		eye.mesh = em
		eye.position = Vector3(px,0.82,-0.40)
		eye.material_override = _mat(Color("#081022"))
		player.add_child(eye)

	var glow = OmniLight3D.new()
	glow.light_color = Color("#8b7cff")
	glow.light_energy = 1.2
	glow.omni_range = 4.0
	glow.position.y = 0.35
	player.add_child(glow)

func _build_camera():
	camera = Camera3D.new()
	camera.fov = 68
	camera.current = true
	camera.position = Vector3(0,6.5,17)
	add_child(camera)

func _build_ui():
	var layer = CanvasLayer.new()
	add_child(layer)

	score_label = _ui_label(layer,"SCORE 000000",Vector2(32,22),Vector2(340,62),34)
	coin_label = _ui_label(layer,"★ 00",Vector2(1090,22),Vector2(150,62),34)
	title_label = _ui_label(layer,"SKYBOUND DASH",Vector2(390,20),Vector2(500,64),42)
	title_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER

	left_btn = _ui_button(layer,"◀",Vector2(36,570),Vector2(120,110),34)
	right_btn = _ui_button(layer,"▶",Vector2(170,570),Vector2(120,110),34)
	jump_btn = _ui_button(layer,"JUMP",Vector2(1085,555),Vector2(165,125),26)
	restart = _ui_button(layer,"RESTART",Vector2(510,420),Vector2(260,82),26)
	restart.visible = false

	left_btn.button_down.connect(func(): left_on=true)
	left_btn.button_up.connect(func(): left_on=false)
	right_btn.button_down.connect(func(): right_on=true)
	right_btn.button_up.connect(func(): right_on=false)
	jump_btn.button_down.connect(func(): jump_request=true)
	restart.pressed.connect(func(): get_tree().reload_current_scene())

func _ui_label(layer,text_value,pos,size,font_size):
	var l = Label.new()
	l.text = text_value
	l.position = pos
	l.size = size
	l.add_theme_font_size_override("font_size",font_size)
	l.add_theme_color_override("font_color",Color("#f7fbff"))
	l.add_theme_color_override("font_shadow_color",Color(0,0,0,0.55))
	l.add_theme_constant_override("shadow_offset_x",3)
	l.add_theme_constant_override("shadow_offset_y",4)
	layer.add_child(l)
	return l

func _ui_button(layer,text_value,pos,size,font_size):
	var b = Button.new()
	b.text = text_value
	b.position = pos
	b.size = size
	b.add_theme_font_size_override("font_size",font_size)
	var s = StyleBoxFlat.new()
	s.bg_color = Color(0.08,0.13,0.28,0.88)
	s.corner_radius_top_left = 25
	s.corner_radius_top_right = 25
	s.corner_radius_bottom_left = 25
	s.corner_radius_bottom_right = 25
	s.border_width_left = 2
	s.border_width_top = 2
	s.border_width_right = 2
	s.border_width_bottom = 2
	s.border_color = Color("#7dd3fc")
	s.shadow_color = Color(0,0,0,0.35)
	s.shadow_size = 8
	b.add_theme_stylebox_override("normal",s)
	var p = s.duplicate()
	p.bg_color = Color(0.27,0.20,0.52,0.96)
	b.add_theme_stylebox_override("pressed",p)
	layer.add_child(b)
	return b

func _make_platform(pos,size,color_index):
	var palette = [Color("#65e6b0"),Color("#66c8ff"),Color("#ad8cff")]
	_make_box(pos,size,palette[color_index])

func _make_box(pos,size,color):
	var body = StaticBody3D.new()
	body.position = pos
	var mesh = MeshInstance3D.new()
	var bm = BoxMesh.new()
	bm.size = size
	mesh.mesh = bm
	mesh.material_override = _mat(color,0.05,0.10)
	body.add_child(mesh)
	var col = CollisionShape3D.new()
	var bs = BoxShape3D.new()
	bs.size = size
	col.shape = bs
	body.add_child(col)
	add_child(body)
	return body

func _make_obstacle(pos,color):
	var ob = _make_box(pos,Vector3(1.3,1.7,1.3),color)
	obstacles.append(ob)

func _make_coin(pos):
	var c = MeshInstance3D.new()
	var tm = TorusMesh.new()
	tm.inner_radius = 0.30
	tm.outer_radius = 0.48
	c.mesh = tm
	c.position = pos
	c.material_override = _mat(Color("#ffd84d"),0.12,0.6)
	c.set_meta("y",pos.y)
	add_child(c)
	coins.append(c)

func _make_enemy(pos,phase):
	var e = MeshInstance3D.new()
	var sm = SphereMesh.new()
	sm.radius = 0.72
	sm.height = 1.44
	e.mesh = sm
	e.position = pos
	e.material_override = _mat(Color("#ff657b"),0.12,0.25)
	e.set_meta("bx",pos.x)
	e.set_meta("phase",phase)
	add_child(e)
	enemies.append(e)

func _make_goal():
	var root = Node3D.new()
	root.position = Vector3(0,2.1,GOAL_Z)
	add_child(root)
	for x in [-2.4,2.4]:
		var pillar = MeshInstance3D.new()
		var bm = BoxMesh.new()
		bm.size = Vector3(0.6,4.8,0.6)
		pillar.mesh = bm
		pillar.position.x = x
		pillar.material_override = _mat(Color("#f8fafc"),0.05,0.15)
		root.add_child(pillar)
	var top = MeshInstance3D.new()
	var topm = BoxMesh.new()
	topm.size = Vector3(5.4,0.65,0.65)
	top.mesh = topm
	top.position.y = 2.1
	top.material_override = _mat(Color("#f59cff"),0.05,0.4)
	root.add_child(top)

func _collect_coins():
	for i in range(coins.size()-1,-1,-1):
		var c = coins[i]
		if is_instance_valid(c) and player.global_position.distance_to(c.global_position) < 1.0:
			coin_count += 1
			score += 100
			c.queue_free()
			coins.remove_at(i)
			coin_label.text = "★ %02d" % coin_count

func _hit_enemies():
	for e in enemies:
		if is_instance_valid(e) and player.global_position.distance_to(e.global_position) < 1.3:
			if player.velocity.y < -1 and player.position.y > e.position.y + 0.35:
				player.velocity.y = JUMP * 0.75
				score += 250
				e.queue_free()
			else:
				_lose("OUCH!")

func _hit_obstacles():
	for o in obstacles:
		if is_instance_valid(o) and player.global_position.distance_to(o.global_position) < 1.2:
			_lose("CRASH!")

func _lose(reason):
	if ended:
		return
	ended = true
	title_label.text = reason
	title_label.position = Vector2(390,300)
	title_label.size = Vector2(500,90)
	restart.visible = true
	left_btn.disabled = true
	right_btn.disabled = true
	jump_btn.disabled = true

func _win():
	if ended:
		return
	ended = true
	won = true
	title_label.text = "LEVEL COMPLETE!"
	title_label.position = Vector2(390,300)
	title_label.size = Vector2(500,90)
	restart.text = "PLAY AGAIN"
	restart.visible = true
	left_btn.disabled = true
	right_btn.disabled = true
	jump_btn.disabled = true

func _mat(color,metallic=0.0,emission=0.0):
	var m = StandardMaterial3D.new()
	m.albedo_color = color
	m.metallic = metallic
	m.roughness = 0.42
	if emission > 0:
		m.emission_enabled = true
		m.emission = color
		m.emission_energy_multiplier = emission
	return m
