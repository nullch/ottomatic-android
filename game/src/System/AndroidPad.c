// ANDROID ON-SCREEN GAMEPAD BRIDGE
//
// The Java overlay (GamepadOverlay) sends its state here through JNI.
// Once per frame, the game thread copies that state into a *virtual SDL gamepad*,
// so the rest of the game (Input.c) sees an ordinary Xbox-style controller.

#if __ANDROID__

#include <SDL3/SDL.h>
#include <jni.h>

// Bit layout used by the Java side (keep in sync with GamepadOverlay.java)
enum
{
	PAD_A		= 1 << 0,	// SOUTH: jump / confirm
	PAD_B		= 1 << 1,	// EAST : punch-pickup / back
	PAD_X		= 1 << 2,	// WEST : shoot / delete
	PAD_Y		= 1 << 3,	// NORTH: text entry space
	PAD_LB		= 1 << 4,	// prev weapon
	PAD_RB		= 1 << 5,	// next weapon
	PAD_START	= 1 << 6,	// pause
	PAD_BACK	= 1 << 7,	// back
	PAD_R3		= 1 << 8,	// camera mode
	PAD_L3		= 1 << 9,
};

static SDL_AtomicInt	sButtons, sLX, sLY, sRX, sRY;
static SDL_Joystick*	sJoystick = NULL;

JNIEXPORT void JNICALL
Java_ru_nullch_ottomaticfork_OttoActivity_nativeSetPad(JNIEnv* env, jclass cls,
		jint buttons, jint lx, jint ly, jint rx, jint ry)
{
	(void) env; (void) cls;
	SDL_SetAtomicInt(&sButtons, buttons);
	SDL_SetAtomicInt(&sLX, lx);
	SDL_SetAtomicInt(&sLY, ly);
	SDL_SetAtomicInt(&sRX, rx);
	SDL_SetAtomicInt(&sRY, ry);
}

void AndroidPad_Init(void)
{
	SDL_VirtualJoystickDesc desc;
	SDL_INIT_INTERFACE(&desc);
	desc.type		= SDL_JOYSTICK_TYPE_GAMEPAD;	// => standard SDL_GamepadButton/Axis layout
	desc.naxes		= SDL_GAMEPAD_AXIS_COUNT;
	desc.nbuttons	= SDL_GAMEPAD_BUTTON_COUNT;
	desc.name		= "On-screen gamepad";

	SDL_JoystickID id = SDL_AttachVirtualJoystick(&desc);
	if (!id)
	{
		SDL_Log("AndroidPad: couldn't attach virtual joystick: %s", SDL_GetError());
		return;
	}

	sJoystick = SDL_OpenJoystick(id);
	if (!sJoystick)
		SDL_Log("AndroidPad: couldn't open virtual joystick: %s", SDL_GetError());
}

static inline Sint16 Clamp16(int v)
{
	return (Sint16) (v < -32768 ? -32768 : (v > 32767 ? 32767 : v));
}

void AndroidPad_Apply(void)
{
	if (!sJoystick)
		return;

	const int b = SDL_GetAtomicInt(&sButtons);

	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_SOUTH,			(b & PAD_A) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_EAST,			(b & PAD_B) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_WEST,			(b & PAD_X) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_NORTH,			(b & PAD_Y) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_LEFT_SHOULDER,	(b & PAD_LB) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER,	(b & PAD_RB) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_START,			(b & PAD_START) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_BACK,			(b & PAD_BACK) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_RIGHT_STICK,		(b & PAD_R3) != 0);
	SDL_SetJoystickVirtualButton(sJoystick, SDL_GAMEPAD_BUTTON_LEFT_STICK,		(b & PAD_L3) != 0);

	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_LEFTX,  Clamp16(SDL_GetAtomicInt(&sLX)));
	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_LEFTY,  Clamp16(SDL_GetAtomicInt(&sLY)));
	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_RIGHTX, Clamp16(SDL_GetAtomicInt(&sRX)));
	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_RIGHTY, Clamp16(SDL_GetAtomicInt(&sRY)));

	// Triggers unused: keep them at "released" (-32768 for virtual gamepads)
	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_LEFT_TRIGGER,  -32768);
	SDL_SetJoystickVirtualAxis(sJoystick, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER, -32768);
}

#endif // __ANDROID__
