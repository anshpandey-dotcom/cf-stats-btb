import type { ComponentProps } from 'react';
import type { MaterialCommunityIcons } from '@expo/vector-icons';

export type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

export type RitualSection = {
  id: string;
  title: string;
  subtitle: string;
  icon: IconName;
  ready: boolean;
  required: boolean;
};

export const RITUAL_SECTIONS: RitualSection[] = [
  {
    id: 'declaration',
    title: 'Warrior Declaration',
    subtitle: 'Say it out loud. Own your day.',
    icon: 'sword-cross',
    ready: true,
    required: true,
  },
  {
    id: 'cold',
    title: 'Cold Awakening',
    subtitle: 'Splash cold water. Wake the body.',
    icon: 'snowflake',
    ready: false,
    required: false,
  },
  {
    id: 'vision',
    title: 'Vision Lock',
    subtitle: 'See the day you will build.',
    icon: 'eye-outline',
    ready: false,
    required: false,
  },
  {
    id: 'gratitude',
    title: 'Gratitude Flame',
    subtitle: 'Three things you are thankful for.',
    icon: 'fire',
    ready: false,
    required: false,
  },
  {
    id: 'move',
    title: 'Body Ignition',
    subtitle: 'Move before the world moves.',
    icon: 'run-fast',
    ready: false,
    required: false,
  },
];

export const DECLARATION_TEXT =
  "This is another day of my life, and I can choose to be in the top 1% of the population, " +
  "or I can be in the 99% with a normal, easy life. Thank God, I choose the warrior mentality. " +
  "I will make my day like the world's number one leader, the world's number one businessman, " +
  "the world's number one athlete. And I think I can do even better than them.";

export const VOICE_PASS_RATIO = 0.85;
