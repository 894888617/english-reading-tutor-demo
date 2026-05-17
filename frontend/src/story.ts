export interface StoryPage {
  page: number;
  sentences: string[];
}

export interface StoryResponse {
  title: string;
  level: string;
  pages: StoryPage[];
}

export const fallbackStory: StoryResponse = {
  title: 'The Little Rabbit',
  level: 'Beginner',
  pages: [
    {
      page: 1,
      sentences: [
        'The little rabbit is looking for his red hat.',
        'He asks the bird, have you seen my hat?',
      ],
    },
    {
      page: 2,
      sentences: [
        'The bird says, look under the tree.',
        'The rabbit finds his red hat.',
      ],
    },
  ],
};
